package com.aircargo.common.event;

import java.util.UUID;

/**
 * Published by the uld-service whenever a ULD is created/updated/assigned/
 * transferred/deleted. Consumers: load-planning-service (invalidates cached
 * load plans so ULD status/assignment changes reflect immediately).
 */
public record UldUpdatedEvent(
    UUID uldId,
    String uldNumber,
    UUID flightId
) {}