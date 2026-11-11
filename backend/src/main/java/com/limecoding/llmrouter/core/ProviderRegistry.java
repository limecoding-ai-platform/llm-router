package com.limecoding.llmrouter.core;

import com.limecoding.llmrouter.core.model.UnKnownProviderException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProviderRegistry {

    private final Map<String, ProviderAdapter> adapters;

    public ProviderRegistry(List<ProviderAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ProviderAdapter::id, Function.identity()));
    }

    public ProviderAdapter get(String id) {
        ProviderAdapter adapter = adapters.get(id);
        if (adapter == null) throw new UnKnownProviderException(id);

        return adapter;
    }

    public Set<String> ids() {
        return adapters.keySet();
    }
}
