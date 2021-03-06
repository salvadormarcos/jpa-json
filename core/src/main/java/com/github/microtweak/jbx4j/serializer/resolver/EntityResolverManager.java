package com.github.microtweak.jbx4j.serializer.resolver;

import com.github.microtweak.jbx4j.serializer.exception.JpaEntityResolverNotFoundException;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Comparator.comparing;

/**
 * Service that stores and locate the most appropriate EntityResolver to resolve the JPA Entity according to the parameters received.
 *
 * @author Marcos Koch Salvador
 * @since 1.0.0
 */
public class EntityResolverManager {

    @SuppressWarnings("rawtypes")
    private Set<EntityResolver> resolvers = new HashSet<>();

    @SuppressWarnings("rawtypes")
    private Map<ResolverPoint, EntityResolver> cache = new ConcurrentHashMap<>();

    /**
     * Registers an EntityResolver by instance.
     *
     * @param resolver Instance of class that implements EntityResolver.
     * @param <E> Generic type representing the JPA Entity to be resolved.
     */
    public <E> void add(EntityResolver<E> resolver) {
        Objects.requireNonNull(resolver, "Provide a valid instance of EntityResolver!");
        resolvers.add(resolver);
    }

    /**
     * Registers an EntityResolver by class.
     *
     * @param clazz Class that implements EntityResolver.
     * @param <E> Generic type representing the JPA entity to be resolved.
     * @param <ER> Generic type representing the instance of EntityResolver.
     */
    public <E, ER extends EntityResolver<E>> void add(Class<ER> clazz) {
        Objects.requireNonNull(clazz, "Provide a valid instance of EntityResolver!");

        try {
            ER resolver = clazz.newInstance();
            this.add(resolver);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Cannot to instantiate EntityResolver %s", clazz.getName()), e);
        }
    }

    /**
     * Locate an EntityResolver to resolve a JPA Entity.
     *
     * @param parent If you are resolving an entity that is a nested attribute/property, this parameter represents the object that contains this attribute/property.
     *               If the entity represents the root element of JSON (or any other format) this parameter will be null.
     * @param rawType Class representing the JPA Entity.
     * @param annotations If you are resolving an entity that is a nested attribute / property, this parameter will represent a List with all annotations of this attribute / property. Otherwise, it will be an empty list.
     *
     * @return EntityResolver instance chosen to resolve the requested Entity.
     *
     * @throws JpaEntityResolverNotFoundException If no EntityResolver is able to resolve the requested Entity.
     */
    @SuppressWarnings("unchecked")
    public EntityResolver<?> lookupResolver(Object parent, Class<?> rawType, List<Annotation> annotations) {
        Objects.requireNonNull(rawType, "Failed to execute the search EntityResolver. The \"rawType\" parameter is required to perform the search!");
        Objects.requireNonNull(annotations, "Failed to execute the search EntityResolver. The \"annotations\" parameter is required to perform the search!");

        final ResolverPoint rp = new ResolverPoint(parent, rawType, annotations);

        final EntityResolver<?> resolver = cache.computeIfAbsent(rp, nonCached -> resolvers.stream()
                .filter(er -> er.canResolve(nonCached.getParentType(), nonCached.getRawType(), nonCached.getAnnotations()))
                .sorted(comparing(er -> ((EntityResolver) er).getOrdinal()).reversed())
                .findFirst()
                .orElse(null)
        );

        if (resolver == null) {
            final String msg = String.format("No EntityResolver found for entity \"%s\"!", rawType.getName());
            throw new JpaEntityResolverNotFoundException(msg);
        }

        return resolver;
    }

}
