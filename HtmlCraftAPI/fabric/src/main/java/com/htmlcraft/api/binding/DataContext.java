package com.htmlcraft.api.binding;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据绑定上下文。
 * 存储 {{variable}} 模板变量，供 HTML 模板渲染时替换。
 * 线程安全：使用 ConcurrentHashMap。
 */
public class DataContext {
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key) {
        Object val = data.get(key);
        return val == null ? "" : val.toString();
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public Map<String, Object> getAll() {
        return new java.util.HashMap<>(data);
    }

    public void clear() {
        data.clear();
    }
}
