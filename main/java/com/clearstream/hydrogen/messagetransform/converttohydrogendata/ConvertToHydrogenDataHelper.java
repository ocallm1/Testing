package com.clearstream.hydrogen.messagetransform.converttohydrogendata;

import com.clearstream.ifs.hydrogen.redex.ChangeNotification;
import com.clearstream.ifs.hydrogen.redex.Column;
import com.clearstream.ifs.hydrogen.redex.PrimaryKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.xerces.dom.ElementImpl;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

@Slf4j
public class ConvertToHydrogenDataHelper {

    static List<Column> getSourceColumnListChangesFor(String currTableName, ChangeNotification notification) {
        return notification.getTableName().stream()
                .filter( name -> currTableName.equals( name.getName() ) ).findFirst()
                .flatMap( tableName -> tableName.getPrimaryKey().stream().findFirst() )
                .map( PrimaryKey::getColumn ).orElseGet( ArrayList::new );
    }

    static List<PrimaryKey> getRowChangesFor(String currTableName, ChangeNotification notification)
    {
        return notification.getTableName().stream().filter(name -> currTableName.equals(name.getName())).map(ChangeNotification.TableName::getPrimaryKey).flatMap(List::stream).collect(Collectors.toList());
    }

    static void debugOldValue(Object oldValue) {
        if (Objects.nonNull(oldValue)) {
            String nodeValue = getFirstNodeValueAsString((ElementImpl) oldValue);
            if(Objects.nonNull(nodeValue)) {
                log.debug("Old value nodeName: " + nodeValue);
            }
        }
    }

    static String getFirstNodeValueAsString(ElementImpl oldValue) {
        Node firstChild = getFirstChildNode(oldValue);
        if(Objects.nonNull(firstChild)) {
            return firstChild.getNodeValue();
        }
        else {
            return "";
        }
    }

    static String getNodeValue(Column column) {
        Element newValue = (Element) column.getNewValue();
        return nonNull( newValue.getFirstChild() ) ? newValue.getFirstChild().getNodeValue() : null;
    }

    static String getOldValue(Column column) {
        Element oldValue = (Element) column.getOldValue();
        return nonNull( oldValue.getFirstChild() ) ? oldValue.getFirstChild().getNodeValue() : null;
    }

    static Node getFirstChildNode(ElementImpl value) {
        ElementImpl elementImpl = value;
        return nonNull( elementImpl.getFirstChild() ) ? elementImpl.getFirstChild() : null;
    }

    /**
     * Returns an array of null properties of an object
     * @param source
     * @return
     */
    static String[] getNullPropertyNames (Object source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();
        Set emptyNames = new HashSet();
        for(java.beans.PropertyDescriptor pd : pds) {
            //check if value of this property is null then add it to the collection
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null) emptyNames.add(pd.getName());
        }
        String[] result = new String[emptyNames.size()];
        return (String[]) emptyNames.toArray(result);
    }
}
