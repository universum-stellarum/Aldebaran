package br.api.universum.serverconfig.secrets;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact, nonrecursive lookup in a service namespace. Never consults the Spring Environment. */
public final class SecretResolver {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");
    private final Function<String, String> variables;

    public SecretResolver(Function<String, String> variables) {
        this.variables = variables;
    }

    public Object resolve(Object value, String prefix, boolean shared) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(key.toString(), resolve(item, prefix, shared)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> resolve(item, prefix, shared)).toList();
        }
        if (value instanceof String text) {
            return resolveText(text, prefix, shared);
        }
        return value;
    }

    private String resolveText(String text, String prefix, boolean shared) {
        var matcher = PLACEHOLDER.matcher(text);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (shared || !name.startsWith(prefix)) {
                throw new DistributionException();
            }
            String replacement = requiredVariable(name);
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        if (containsExpression(output.toString())) {
            throw new DistributionException();
        }
        return output.toString();
    }

    private String requiredVariable(String name) {
        String value = variables.apply(name);
        if (value == null || value.isBlank() || containsExpression(value)) {
            throw new DistributionException();
        }
        return value;
    }

    private boolean containsExpression(String value) {
        return value.contains("${") || value.startsWith("{cipher}");
    }
}
