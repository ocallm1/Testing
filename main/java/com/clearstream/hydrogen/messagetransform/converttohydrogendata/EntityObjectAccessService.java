package com.clearstream.hydrogen.messagetransform.converttohydrogendata;

import com.clearstream.hydrogen.database.BaseEntity;
import com.clearstream.hydrogen.database.CreShareClass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Service
public class EntityObjectAccessService {

    public  <T> T getObjectFromMap ( Class<T> clazz,Map<String, BaseEntity> propertiesToSaveMap){
        try {
            T object = (T) propertiesToSaveMap.get(clazz.getName());
            if (object == null) {
                object = clazz.getDeclaredConstructor().newInstance();
                BaseEntity newEntity=(BaseEntity)object;
                newEntity.setCreateTs(Instant.now());
                newEntity.setCreatedBy(RedexDefinitions.REDEX);
                propertiesToSaveMap.put(clazz.getName(),newEntity);

            }
            return object;
        }catch(Exception exception){
            log.error(exception.getMessage(),exception);
        }

        return null;
    }

    public CreShareClass getShareClassFromMap(Map propertiesToSaveMap) {
        CreShareClass creShareClass = getObjectFromMap(CreShareClass.class, propertiesToSaveMap);

        return creShareClass;
    }

}
