package com.aircargo.notificationservice.listener;

import com.aircargo.common.event.AuditLogEvent;
import com.aircargo.common.event.BookingAwbUpdatedEvent;
import com.aircargo.common.event.FlightDepartedEvent;
import com.aircargo.common.event.MawbStatusChangedEvent;
import com.aircargo.common.event.ReceiptCreatedEvent;
import com.aircargo.feign.client.AuthClient;
import com.aircargo.feign.dto.UserDTO;
import com.aircargo.notificationservice.entity.AuditLog;
import com.aircargo.notificationservice.repository.AuditLogRepository;
import com.aircargo.notificationservice.service.EmailNotificationService;
import com.aircargo.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumidor único de la cola aircargo.notifications.
 * Un @RabbitHandler por tipo de evento evita el round-robin entre listeners
 * que causaba notificaciones equivocadas.
 */
@Component
@ConditionalOnProperty(name = "spring.rabbitmq.listener.simple.auto-startup", havingValue = "true", matchIfMissing = true)
@RabbitListener(queues = "aircargo.notifications", containerFactory = "retryListenerFactory")
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;
    private final AuthClient authClient;
    private final AuditLogRepository auditLogRepository;

    public NotificationEventListener(NotificationService notificationService,
                                     EmailNotificationService emailNotificationService,
                                     AuthClient authClient,
                                     AuditLogRepository auditLogRepository) {
        this.notificationService = notificationService;
        this.emailNotificationService = emailNotificationService;
        this.authClient = authClient;
        this.auditLogRepository = auditLogRepository;
    }

    @RabbitHandler
    public void onAuditLog(AuditLogEvent event) {
        log.info("Received audit.log event: action={}, entity={}, entityId={}",
                event.action(), event.entityType(), event.entityId());
        try {
            auditLogRepository.save(new AuditLog(
                    event.userId(), event.email(), event.fullName(), event.action(),
                    event.entityType(), event.entityId(), event.details(), event.ipAddress()));
        } catch (Exception e) {
            log.error("Failed to persist audit.log event: {}", e.getMessage(), e);
        }
    }

    @RabbitHandler
    public void onReceiptCreated(ReceiptCreatedEvent event) {
        log.info("Received receipt.created event: receiptId={}, mawbId={}, awbNumber={}",
                event.receiptId(), event.mawbId(), event.mawbNumber());
        try {
            for (UserDTO user : authClient.getAllUsers()) {
                notificationService.createNotification(
                        user.getId(),
                        "EMAIL",
                        "Recibo de Bodega Emitido",
                        "Se ha emitido un recibo de bodega para el AWB " + event.mawbNumber()
                                + ". ID del recibo: " + event.receiptId(),
                        "RECEIPT",
                        event.receiptId()
                );
                if (user.getEmail() != null) {
                    emailNotificationService.sendReceiptNotification(
                            user.getId(), user.getEmail(), event.mawbNumber(), event.receiptId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process receipt.created event: {}", e.getMessage(), e);
        }
    }

    @RabbitHandler
    public void onBookingAwbUpdated(BookingAwbUpdatedEvent event) {
        log.info("Received booking.awb.updated event: {}", event);
        try {
            for (UserDTO user : authClient.getAllUsers()) {
                notificationService.createNotification(
                        user.getId(),
                        "EMAIL",
                        "Reserva Confirmada",
                        "Se ha confirmado una reserva en el sistema. AWB: " + event.awbNumber(),
                        "BOOKING",
                        event.bookingId()
                );
                if (user.getEmail() != null) {
                    emailNotificationService.sendBookingConfirmation(
                            user.getId(), user.getEmail(), event.awbNumber(), event.bookingId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process booking.awb.updated event: {}", e.getMessage(), e);
        }
    }

    @RabbitHandler
    public void onFlightDeparted(FlightDepartedEvent event) {
        log.info("Received flight.departed event: {}", event);
        try {
            for (UserDTO user : authClient.getAllUsers()) {
                notificationService.createNotification(
                        user.getId(),
                        "EMAIL",
                        "Vuelo Ha Partido",
                        "El vuelo " + event.flightNumber() + " ha partido.",
                        "FLIGHT",
                        event.flightId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to process flight.departed event: {}", e.getMessage(), e);
        }
    }

    @RabbitHandler
    public void onMawbStatusChanged(MawbStatusChangedEvent event) {
        log.info("Received mawb.status.changed event: {}", event);
        try {
            for (UserDTO user : authClient.getAllUsers()) {
                notificationService.createNotification(
                        user.getId(),
                        "EMAIL",
                        "Estado MAWB Actualizado",
                        "El MAWB " + event.awbNumber() + " cambió de " + event.oldStatus()
                                + " a " + event.newStatus() + ".",
                        "MAWB",
                        event.mawbId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to process mawb.status.changed event: {}", e.getMessage(), e);
        }
    }
}
