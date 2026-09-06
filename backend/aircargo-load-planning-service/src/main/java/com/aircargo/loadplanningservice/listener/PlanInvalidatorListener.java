package com.aircargo.loadplanningservice.listener;

import com.aircargo.common.event.MawbUpdatedEvent;
import com.aircargo.common.event.UldUpdatedEvent;
import com.aircargo.loadplanningservice.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Invalidates cached load plans when a MAWB or ULD changes anywhere in the
 * system (mawb.updated / uld.updated). Keeps load-planning near-live with a
 * single queue bound to both routing keys.
 *
 * NOTE: un UNICO @RabbitListener con un @RabbitHandler por tipo de evento —
 * si hubiera dos @RabbitListener sobre la misma cola, el broker reparte los
 * mensajes round-robin entre dos consumidores y un MawbUpdatedEvent puede caer
 * en el handler de UldUpdatedEvent (error de conversion -> evento perdido).
 * Este es el mismo patron usado por NotificationEventListener.
 */
@Component
@RabbitListener(queues = RabbitConfig.INVALIDATE_QUEUE)
public class PlanInvalidatorListener {

    private static final Logger log = LoggerFactory.getLogger(PlanInvalidatorListener.class);

    private final CacheManager cacheManager;

    public PlanInvalidatorListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @RabbitHandler
    public void onMawbUpdated(MawbUpdatedEvent event) {
        invalidate(event.awbNumber());
    }

    @RabbitHandler
    public void onUldUpdated(UldUpdatedEvent event) {
        invalidate(event.uldNumber());
    }

    private void invalidate(String what) {
        Cache cache = cacheManager.getCache("load-plans");
        if (cache != null) {
            cache.clear();
            log.info("load-plans invalidado por cambio en {}", what);
        }
    }
}