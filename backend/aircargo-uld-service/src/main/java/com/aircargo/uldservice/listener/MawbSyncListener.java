package com.aircargo.uldservice.listener;

import com.aircargo.common.entity.CommodityType;
import com.aircargo.common.event.MawbUpdatedEvent;
import com.aircargo.uldservice.config.RabbitConfig;
import com.aircargo.uldservice.entity.UldAwb;
import com.aircargo.uldservice.repository.UldAwbRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consume mawb.updated: mantiene los links uld_awb (description/destination/status)
 * sincronizados con la MAWB como fuente de verdad. Invalida las cachés de ULD para
 * que el siguiente read sirva commodity/status frescos.
 */
@Component
public class MawbSyncListener {

    private static final Logger log = LoggerFactory.getLogger(MawbSyncListener.class);

    private final UldAwbRepository uldAwbRepository;

    public MawbSyncListener(UldAwbRepository uldAwbRepository) {
        this.uldAwbRepository = uldAwbRepository;
    }

    @RabbitListener(queues = RabbitConfig.MAWB_SYNC_QUEUE)
    @Transactional
    @CacheEvict(value = {"ulds", "uld-awbs"}, allEntries = true)
    public void onMawbUpdated(MawbUpdatedEvent event) {
        try {
            List<UldAwb> links = event.mawbId() != null
                    ? uldAwbRepository.findByMawbId(event.mawbId())
                    : List.of();
            if (links.isEmpty() && event.awbNumber() != null) {
                links = uldAwbRepository.findByMawbLabel(event.awbNumber());
            }
            if (links.isEmpty()) return;

            CommodityType commodity = safeCommodity(event.commodityType());
            for (UldAwb link : links) {
                if (commodity != null) link.setDescription(commodity);
                link.setStatus(event.status());
                if (event.destination() != null) link.setDestination(event.destination());
            }
            uldAwbRepository.saveAll(links);
            log.info("mawb.updated {} -> {} uld_awb link(s) sincronizados (commodity={}, status={})",
                    event.awbNumber(), links.size(), commodity, event.status());
        } catch (Exception e) {
            log.error("Fallo la sincronizacion de mawb.updated {}: {}", event.awbNumber(), e.getMessage());
        }
    }

    private CommodityType safeCommodity(String raw) {
        if (raw == null || raw.isBlank()) return CommodityType.DRY_CARGO;
        try {
            return CommodityType.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("CommodityType desconocido '{}' en mawb.updated; se mantiene el existente", raw);
            return null;
        }
    }
}