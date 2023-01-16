package eu.koboo.en2do.test.alien;

import eu.koboo.en2do.Collection;
import eu.koboo.en2do.Repository;
import eu.koboo.en2do.meta.options.DropEntitiesOnStart;
import eu.koboo.en2do.meta.options.DropIndexesOnStart;

import java.util.UUID;

@Collection("alien_repository")
@DropIndexesOnStart
@DropEntitiesOnStart
public interface AlienRepository extends Repository<Alien, UUID> {
}
