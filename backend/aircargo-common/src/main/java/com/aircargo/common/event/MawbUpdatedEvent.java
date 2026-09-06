package com.aircargo.common.event;

import java.util.UUID;

/**
 * Published by the mawb-service whenever a MAWB's commodity/destination/status
 * changes. Consumers: uld-service (keeps uld_awb links in sync) and
 * load-planning-service (invalidates cached load plans).
 */
public record MawbUpdatedEvent(
    UUID mawbId,
    String awbNumber,
    String commodityType,
    String status,
    String destination
) {}