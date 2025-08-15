package org.javers.core.metamodel.type;

import org.javers.common.collections.EnumerableFunction;
import org.javers.common.collections.Maps;
import org.javers.common.validation.Validate;
import org.javers.core.metamodel.object.OwnerContext;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author bartosz walacik
 */
public class MapType extends KeyValueType {
    private final List<Type> concreteTypeArguments;

    public MapType(Type baseJavaType, TypeMapperLazy typeMapperlazy) {
        super(baseJavaType, 2, typeMapperlazy);

        if (super.getKeyJavaType() == DEFAULT_TYPE_PARAMETER && super.getValueJavaType() == DEFAULT_TYPE_PARAMETER) {
            concreteTypeArguments = buildMapTypeArguments(getBaseJavaClass());
        } else {
            concreteTypeArguments = null;
        }
    }

    private static List<Type> buildMapTypeArguments(Class<?> baseJavaClass) {
        if (baseJavaClass == Map.class) {
            return null; // Handled by base class
        }

        for (Class<?> current = baseJavaClass; current != null; current = current.getSuperclass()) {
            Type superClass = current.getGenericSuperclass();
            if (superClass instanceof ParameterizedType) {
                Type rawType = ((ParameterizedType) superClass).getRawType();
                if (rawType instanceof Class && Map.class.isAssignableFrom((Class<?>) rawType)) {
                    return buildListOfConcreteTypeArguments(superClass, 2);
                }
            }

            Type[] interfaces = current.getGenericInterfaces();
            Optional<Type> mapInterface = Arrays.stream(interfaces)
                    .filter(it -> it instanceof ParameterizedType)
                    .filter(it -> ((ParameterizedType) it).getRawType() instanceof Class)
                    .filter(it -> Map.class.isAssignableFrom((Class<?>) ((ParameterizedType) it).getRawType()))
                    .findFirst();
            if (mapInterface.isPresent()) {
                return buildListOfConcreteTypeArguments(mapInterface.get(), 2);
            }
        }

        throw new IllegalStateException("baseJavaType is not a Map");
    }

    @Override
    public Type getKeyJavaType() {
        if (concreteTypeArguments == null) {
            return super.getKeyJavaType();
        }
        return concreteTypeArguments.get(0);
    }

    @Override
    public Type getValueJavaType() {
        if (concreteTypeArguments == null) {
            return super.getValueJavaType();
        }
        return concreteTypeArguments.get(1);
    }

    /**
     * @return immutable Map
     */
    @Override
    public Object map(Object sourceEnumerable, EnumerableFunction mapFunction, OwnerContext owner) {
        Validate.argumentsAreNotNull(mapFunction, owner);

        Map sourceMap = Maps.wrapNull(sourceEnumerable);
        Map targetMap = new HashMap(sourceMap.size());
        MapEnumerationOwnerContext enumeratorContext = new MapEnumerationOwnerContext(this, owner);

        mapEntrySet(this, sourceMap.entrySet(), mapFunction, enumeratorContext, (k,v) ->  targetMap.put(k,v), false);

        return Collections.unmodifiableMap(targetMap);
    }

    @Override
    public Object map(Object source, Function mapFunction, boolean filterNulls) {
        Validate.argumentsAreNotNull(mapFunction);

        Map sourceMap = Maps.wrapNull(source);
        Map targetMap = new HashMap(sourceMap.size());

        mapEntrySet(this, sourceMap.entrySet(), mapFunction, (k,v) -> targetMap.put(k,v), filterNulls);

        return Collections.unmodifiableMap(targetMap);
    }

    @Override
    public boolean isEmpty(Object map) {
        return map == null || ((Map)map).isEmpty();
    }

    public static void mapEntrySet(KeyValueType keyValueType,
                              Collection<Map.Entry<?,?>> sourceEntries,
                              EnumerableFunction mapFunction,
                              MapEnumerationOwnerContext mapEnumerationContext,
                              BiConsumer entryConsumer,
                              boolean filterNulls) {
        for (Map.Entry entry : sourceEntries) {
            //key
            mapEnumerationContext.switchToKey();
            Object mappedKey = mapFunction.apply(entry.getKey(), mapEnumerationContext);
            if (mappedKey == null && filterNulls) continue;

            //value
            mapEnumerationContext.switchToValue(mappedKey);

            Object entryValue = entry.getValue();
            if (entryValue == null) {
              continue;
            }
            Object mappedValue = null;
            if (keyValueType.getValueJaversType() instanceof ContainerType) {
                ContainerType containerType = (ContainerType) keyValueType.getValueJaversType();
                mappedValue = containerType.map(entryValue, mapFunction, mapEnumerationContext);
            } else {
                mappedValue = mapFunction.apply(entryValue, mapEnumerationContext);
            }

            entryConsumer.accept(mappedKey, mappedValue);
        }
    }

    public static void mapEntrySet(KeyValueType keyValueType,
                                   Collection<Map.Entry<?,?>> sourceEntries,
                                   Function mapFunction,
                                   BiConsumer entryConsumer,
                                   boolean filterNulls) {
        MapEnumerationOwnerContext enumeratorContext = MapEnumerationOwnerContext.dummy(keyValueType);
        EnumerableFunction enumerableFunction = (input, ownerContext) -> mapFunction.apply(input);
        mapEntrySet(keyValueType, sourceEntries, enumerableFunction, enumeratorContext, entryConsumer,  filterNulls);
    }

    @Override
    public Object empty() {
        return Collections.emptyMap();
    }

    @Override
    protected Stream<Map.Entry> entries(Object source) {
        Map sourceMap = Maps.wrapNull(source);
        return sourceMap.entrySet().stream();
    }

    @Override
    public Class<?> getEnumerableInterface() {
        return Map.class;
    }
}
