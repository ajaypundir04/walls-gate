package com.wallsgate.policy;

import com.wallsgate.domain.repository.PolicyRepository;
import com.wallsgate.model.Models.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of {@link PolicyRepository}.
 * Manages matters, walls, user-to-matter access permissions, and knowledge base documents.
 */
@Component
public class PolicyStore implements PolicyRepository {

    private final Map<String, Matter> matters = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userMatters = new ConcurrentHashMap<>();
    private final Set<Wall> walls = ConcurrentHashMap.newKeySet();
    private final Map<String, Doc> docs = new ConcurrentHashMap<>();

    @Override
    public void putMatter(Matter m) {
        matters.put(m.id(), m);
    }

    @Override
    public Collection<Matter> matters() {
        return Collections.unmodifiableCollection(matters.values());
    }

    @Override
    public Optional<Matter> matter(String id) {
        return Optional.ofNullable(matters.get(id));
    }

    @Override
    public void putWall(Wall w) {
        walls.add(w);
    }

    @Override
    public Set<Wall> walls() {
        return Set.copyOf(walls);
    }

    @Override
    public void putUser(User u) {
        userMatters.put(u.id(), Set.copyOf(u.matters()));
    }

    @Override
    public Optional<User> user(String id) {
        Set<String> m = userMatters.get(id);
        return m == null ? Optional.empty() : Optional.of(new User(id, m));
    }

    @Override
    public Collection<User> users() {
        return userMatters.entrySet().stream()
                .map(e -> new User(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public void putDoc(Doc d) {
        docs.put(d.id(), d);
    }

    @Override
    public Collection<Doc> docs() {
        return Collections.unmodifiableCollection(docs.values());
    }
}
