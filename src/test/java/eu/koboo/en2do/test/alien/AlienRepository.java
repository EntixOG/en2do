package eu.koboo.en2do.test.alien;

import eu.koboo.en2do.repository.Collection;
import eu.koboo.en2do.repository.DropEntitiesOnStart;
import eu.koboo.en2do.repository.DropIndexesOnStart;
import eu.koboo.en2do.repository.Repository;

import java.util.UUID;

@Collection("alien_repository")
@DropIndexesOnStart
@DropEntitiesOnStart
public interface AlienRepository extends Repository<Alien, UUID> {
}
