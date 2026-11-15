"""Real Docker/HTTP checks using disposable credentials and loopback-only test ports.

Prerequisites: Java 21, Python 3, Docker, ./mvnw test dependency:build-classpath
-Dmdep.outputFile=target/runtime-classpath.txt -DincludeScope=runtime.
"""
import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
IMAGE = sys.argv[1] if len(sys.argv) > 1 else "universum-config-server:verification"


def command(*args):
    result = subprocess.run(args, cwd=ROOT, text=True, capture_output=True)
    if result.returncode:
        # Never echo Docker output: config/inspect may contain credentials.
        raise RuntimeError("Command failed: " + args[0] + " " + args[1])
    return result.stdout.strip()


def require(condition, description):
    if not condition:
        raise AssertionError(description)


def request(port, path, username=None, password=None):
    headers = {"Accept": "application/json"}
    if username:
        encoded = base64.b64encode((username + ":" + password).encode()).decode()
        headers["Authorization"] = "Basic " + encoded
    req = urllib.request.Request(f"http://127.0.0.1:{port}{path}", headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def wait_ready(container, port):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        state = json.loads(command("docker", "inspect", container))[0]["State"]
        require(state["Running"], "Container exited before becoming ready")
        try:
            if request(port, "/actuator/health")[0] == 200:
                return
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(1)
    raise AssertionError("Container did not become ready")


def wait_failed(container):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        state = json.loads(command("docker", "inspect", container))[0]["State"]
        if not state["Running"]:
            require(state["ExitCode"] != 0, "Invalid configuration must exit with failure")
            return
        time.sleep(1)
    raise AssertionError("Invalid configuration unexpectedly kept running")


def main():
    classpath = (ROOT / "target/runtime-classpath.txt").read_text().strip()
    classpath += os.pathsep + str(ROOT / "target/test-classes")
    credentials = command("java", "-cp", classpath,
                          "br.api.universum.serverconfig.DockerSmokeCredentials").splitlines()
    password, password_hash = credentials[-2:]
    containers = []
    networks = []
    try:
        with tempfile.TemporaryDirectory(prefix="docker-smoke-", dir=ROOT / "target") as temp:
            directory = Path(temp)
            deployments = {}
            for index, environment in enumerate(("dev", "prod")):
                internal_port = 8989 + index
                variables = {}
                for line in (ROOT / ".env.example").read_text().splitlines():
                    if line and not line.startswith("#") and "=" in line:
                        name, value = line.split("=", 1)
                        variables[name] = value or ("test-only-" + uuid.uuid4().hex)
                variables.update(APP_ENV=environment, SERVER_PORT=str(internal_port),
                                 CONFIG_SERVER_IMAGE=IMAGE,
                                 CONFIG_CLIENTS_0_USERNAME="auth-" + environment,
                                 CONFIG_CLIENTS_1_USERNAME="finance-" + environment,
                                 CONFIG_CLIENTS_2_NAME="absent-service",
                                 CONFIG_CLIENTS_2_USERNAME="absent-" + environment)
                for name in list(variables):
                    if name.endswith("_PASSWORD_HASH"):
                        variables[name] = password_hash
                variables["DIST_AUTH_SERVICE_DATABASE_PASSWORD"] += "-$literal"
                variables["DIST_AUTH_SERVICE_DATABASE_PORT"] = "5432"
                variables["DIST_FINANCE_SERVICE_DATABASE_PORT"] = "5432"
                environment_directory = directory / environment
                environment_directory.mkdir()
                envfile = environment_directory / ".env"
                envfile.write_text("".join(f"{key}={value}\n" for key, value in variables.items()), encoding="utf-8")
                parsed = json.loads(command("docker", "compose", "--project-directory", str(environment_directory),
                                            "--env-file", str(envfile), "-f", str(ROOT / "compose.yml"),
                                            "config", "--format", "json"))
                service = parsed["services"]["config-server"]
                require(not service.get("ports"), "Default Compose must not publish ports")
                require(service["environment"]["CONFIG_CLIENTS_0_PASSWORD_HASH"] == password_hash.replace("$", "$$"),
                        "Compose must preserve bcrypt hashes")
                require(service["environment"]["DIST_AUTH_SERVICE_DATABASE_PASSWORD"] ==
                        variables["DIST_AUTH_SERVICE_DATABASE_PASSWORD"].replace("$", "$$"), "Compose must preserve literal secrets")
                project = "universum-smoke-" + uuid.uuid4().hex[:10] + "-" + environment
                override = environment_directory / "override.json"
                override.write_text(json.dumps({
                    "services": {"config-server": {"container_name": project,
                        "ports": [f"127.0.0.1::{internal_port}"]}},
                    "networks": {"dokploy-network": {"external": False, "name": project}}
                }), encoding="utf-8")
                compose = ("docker", "compose", "--project-directory", str(environment_directory),
                           "--env-file", str(envfile), "-p", project, "-f", str(ROOT / "compose.yml"),
                           "-f", str(override))
                containers.append(project)
                networks.append(project)
                command(*compose, "up", "--detach", "--no-build")
                container = command(*compose, "ps", "--quiet", "config-server")
                containers[-1] = container
                host_port = int(command("docker", "port", container, f"{internal_port}/tcp").rsplit(":", 1)[1])
                wait_ready(container, host_port)
                deployments[environment] = (container, host_port, variables)
                username = variables["CONFIG_CLIENTS_0_USERNAME"]
                status, body = request(host_port, f"/auth-service/{environment}", username, password)
                require(status == 200, environment + ": authorized request must succeed")
                sources = json.loads(body)["propertySources"]
                merged = {}
                for source in reversed(sources):
                    merged.update(source["source"])
                require(merged["spring.datasource.password"] == variables["DIST_AUTH_SERVICE_DATABASE_PASSWORD"],
                        environment + ": server must resolve actual OS environment")
                require(variables["DIST_FINANCE_SERVICE_DATABASE_PASSWORD"] not in body,
                        environment + ": finance secret leaked")
                require(password_hash not in body and "${" not in body, environment + ": unresolved/internal data")
                for path, expected in (
                    (f"/finance-service/{environment}", 403),
                    ("/auth-service/" + ("prod" if environment == "dev" else "dev"), 403),
                    ("/auth-service/dev,prod", 403),
                    (f"/auth-service/{environment}/main", 403),
                    (f"/auth-service-{environment}.yml", 403),
                    (f"/auth-service/{environment}/secret.txt", 403),
                    ("/actuator/env", 403),
                ):
                    require(request(host_port, path, username, password)[0] == expected,
                            environment + ": rejected route " + path)
                require(request(host_port, f"/auth-service/{environment}")[0] == 401, "Missing credentials")
                require(request(host_port, f"/auth-service/{environment}", username, "invalid")[0] == 401,
                        "Invalid credentials")
                require(request(host_port, f"/absent-service/{environment}",
                                variables["CONFIG_CLIENTS_2_USERNAME"], password)[0] == 404, "Missing application")
                command("docker", "exec", container, "/bin/bash", "/app/healthcheck.sh")
                details = json.loads(command("docker", "inspect", container))[0]
                require(details["Config"]["User"] == "10001:10001", "Non-root user")
                require(details["HostConfig"]["ReadonlyRootfs"], "Read-only root filesystem")
                print(environment + ": HTTP 200/401/403/404, actual secrets, healthcheck and non-root OK", flush=True)

            # Both running instances use the same image and receive only their own deployment variables.
            dev_container, dev_port, dev_values = deployments["dev"]
            _, _, prod_values = deployments["prod"]
            status, body = request(dev_port, "/auth-service/dev", dev_values["CONFIG_CLIENTS_0_USERNAME"], password)
            require(prod_values["DIST_AUTH_SERVICE_DATABASE_PASSWORD"] not in body, "PROD secret leaked into DEV")
            require(request(dev_port, "/auth-service/dev", prod_values["CONFIG_CLIENTS_0_USERNAME"], password)[0] == 401,
                    "PROD identity accepted by DEV")
            for environment, (container, _, values) in deployments.items():
                logs = command("docker", "logs", container)
                for secret in (password, password_hash, values["DIST_AUTH_SERVICE_DATABASE_PASSWORD"],
                               values["DIST_FINANCE_SERVICE_DATABASE_PASSWORD"]):
                    require(secret not in logs, "Secret leaked in container logs")
            print("DEV/PROD isolation and absence of secrets in container logs OK", flush=True)

            for environment, (container, _, _) in deployments.items():
                deadline = time.monotonic() + 60
                while time.monotonic() < deadline:
                    health = json.loads(command("docker", "inspect", container))[0]["State"]["Health"]["Status"]
                    if health == "healthy":
                        break
                    time.sleep(2)
                require(health == "healthy", environment + ": Docker HEALTHCHECK must report healthy")
            print("Docker HEALTHCHECK: both containers healthy", flush=True)

            for args in (("--env-file", str(directory / "dev" / ".env"), "-e", "APP_ENV=qa"),
                         ("-e", "APP_ENV=dev")):
                container = command("docker", "run", "--detach", *args, IMAGE)
                containers.append(container)
                wait_failed(container)
            print("Startup rejects invalid environment and missing security registration OK", flush=True)
    finally:
        for container in containers:
            subprocess.run(["docker", "rm", "--force", container], capture_output=True)
        for network in networks:
            subprocess.run(["docker", "network", "rm", network], capture_output=True)


if __name__ == "__main__":
    main()
