package com.wallsgate.domain.repository;

import com.wallsgate.model.Models.*;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Domain repository abstraction decoupling persistence from business logic (DIP).
 */
public interface PolicyRepository {

    void putMatter(Matter m);

    Collection<Matter> matters();

    Optional<Matter> matter(String id);

    void putWall(Wall w);

    Set<Wall> walls();

    void putUser(User u);

    Optional<User> user(String id);

    Collection<User> users();

    void putDoc(Doc d);

    Collection<Doc> docs();
}
