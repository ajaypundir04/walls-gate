package com.wallsgate.policy;

import com.wallsgate.model.Models.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory source of truth for matters, walls, user->matter access and documents.
 * MVP: replace with a DB and an importer from the firm's DMS / file share ACLs.
 */
@Component
public class PolicyStore {
    private final Map<String, Matter> matters = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userMatters = new ConcurrentHashMap<>();
    private final Set<Wall> walls = ConcurrentHashMap.newKeySet();
    private final Map<String, Doc> docs = new ConcurrentHashMap<>();

    public void putMatter(Matter m) { matters.put(m.id(), m); }
    public Collection<Matter> matters() { return matters.values(); }

    public void putWall(Wall w) { walls.add(w); }
    public Set<Wall> walls() { return Set.copyOf(walls); }

    public void putUser(User u) { userMatters.put(u.id(), Set.copyOf(u.matters())); }
    public Optional<User> user(String id) {
        Set<String> m = userMatters.get(id);
        return m == null ? Optional.empty() : Optional.of(new User(id, m));
    }
    public Collection<User> users() {
        return userMatters.entrySet().stream().map(e -> new User(e.getKey(), e.getValue())).toList();
    }

    public void putDoc(Doc d) { docs.put(d.id(), d); }
    public Collection<Doc> docs() { return docs.values(); }
}
