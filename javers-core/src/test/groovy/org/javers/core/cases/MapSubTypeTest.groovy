package org.javers.core.cases

import org.javers.core.JaversBuilder
import org.javers.core.metamodel.type.MapType
import spock.lang.Specification

/**
 * https://github.com/javers/javers/issues/1450
 * @author Yat Ho
 */
class MapSubtypeTest extends Specification {
    class MyMap extends HashMap<String, String> {}

    def "should recognise key and value types from Map subtypes"() {
        given:
        def javers = JaversBuilder.javers().build()

        when:
        def jType = javers.<MapType>getTypeMapping(MyMap)
        def expectedJType = javers.<MapType>getTypeMapping(MyMap.getGenericSuperclass())

        then:
        jType.getKeyJavaType() == expectedJType.getKeyJavaType()
        jType.getValueJavaType() == expectedJType.getValueJavaType()
    }
}
