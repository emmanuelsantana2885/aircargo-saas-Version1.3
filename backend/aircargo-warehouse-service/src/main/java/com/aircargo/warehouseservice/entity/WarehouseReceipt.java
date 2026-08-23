package com.aircargo.warehouseservice.entity;

import com.aircargo.common.crypto.CryptoAttributeConverter;
import com.aircargo.common.entity.CommodityType;
import jakarta.persistence.*;

import java.math.BigDecimal;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "warehouse_receipt")
public class WarehouseReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "airline_id", nullable = false)
    private UUID airlineId;

    @Column(name = "mawb_id")
    private UUID mawbId;

    @Column(name = "mawb_number", length = 50)
    private String mawbNumber;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "gateway_cfs", length = 50)
    private String gatewayCfs;

    @Column(name = "shipper_name", length = 200)
    private String shipperName;

    @Column(name = "consignee_name", length = 200)
    private String consigneeName;

    @Column(name = "agent_name", length = 200)
    private String agentName;

    @Column(name = "origin", length = 3)
    private String origin;

    @Column(name = "destination", length = 3)
    private String destination;

    @Column(name = "awb_reported_pieces")
    private Integer awbReportedPieces;

    @Column(name = "mawb_weight_greatest", precision = 10, scale = 3)
    private BigDecimal mawbWeightGreatest;

    @Column(name = "shipper_reported_weight", precision = 10, scale = 3)
    private BigDecimal shipperReportedWeight;

    @Column(name = "start_datetime")
    private OffsetDateTime startDatetime;

    @Column(name = "receipt_date")
    private OffsetDateTime receiptDate;

    @Column(name = "cash_only")
    private Boolean cashOnly;

    @Column(name = "booked_in_acoms")
    private Boolean bookedInAcoms;

    @Column(name = "docs_provided")
    private Boolean docsProvided;

    @Column(name = "customs_completed")
    private Boolean customsCompleted;

    @Column(name = "pre_built")
    private Boolean preBuilt;

    @Column(name = "loose_tender")
    private Boolean looseTender;

    @Column(name = "piece_count")
    private Integer pieceCount;

    @Column(name = "dim_factor_dom")
    private Integer dimFactorDom;

    @Column(name = "dim_factor_intl")
    private Integer dimFactorIntl;

    @Column(name = "actual_weight_lbs", precision = 10, scale = 2)
    private BigDecimal actualWeightLbs;

    @Column(name = "actual_weight_kg", precision = 10, scale = 3)
    private BigDecimal actualWeightKg;

    @Column(name = "chargeable_weight_lbs", precision = 10, scale = 2)
    private BigDecimal chargeableWeightLbs;

    @Column(name = "chargeable_weight_kg", precision = 10, scale = 3)
    private BigDecimal chargeableWeightKg;

    @Column(name = "shipper_comment", columnDefinition = "TEXT")
    private String shipperComment;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    @Column(name = "delivered_by_name", length = 150)
    private String deliveredByName;

    @Column(name = "delivered_by_id_num", length = 50)
    @Convert(converter = CryptoAttributeConverter.class)
    private String deliveredByIdNum;

    @Column(name = "delivered_by_id_doc_url", columnDefinition = "TEXT")
    @Convert(converter = CryptoAttributeConverter.class)
    private String deliveredByIdDocUrl;

    @Column(name = "delivered_by_sig_url", columnDefinition = "TEXT")
    @Convert(converter = CryptoAttributeConverter.class)
    private String deliveredBySigUrl;

    @Column(name = "received_by_name", length = 150)
    private String receivedByName;

    @Column(name = "received_by_id_num", length = 50)
    @Convert(converter = CryptoAttributeConverter.class)
    private String receivedByIdNum;

    @Column(name = "received_by_id_doc_url", columnDefinition = "TEXT")
    @Convert(converter = CryptoAttributeConverter.class)
    private String receivedByIdDocUrl;

    @Column(name = "received_by_sig_url", columnDefinition = "TEXT")
    @Convert(converter = CryptoAttributeConverter.class)
    private String receivedBySigUrl;

    @Column(name = "broker_name", length = 150)
    private String brokerName;

    @Column(name = "broker_id_num", length = 50)
    @Convert(converter = CryptoAttributeConverter.class)
    private String brokerIdNum;

    @Column(name = "broker_id_doc_url", columnDefinition = "TEXT")
    @Convert(converter = CryptoAttributeConverter.class)
    private String brokerIdDocUrl;

    @Column(name = "broker_sig_url", columnDefinition = "TEXT")
    @Convert(converter = CryptoAttributeConverter.class)
    private String brokerSigUrl;

    @Column(name = "receipt_doc_url", columnDefinition = "TEXT")
    private String receiptDocUrl;

    @Column(name = "dock_signature", columnDefinition = "TEXT")
    @Convert(converter = CryptoAttributeConverter.class)
    private String dockSignature;

    @Column(name = "supporting_docs", columnDefinition = "TEXT")
    private String supportingDocs;

    @Column(name = "hawb_id")
    private UUID hawbId;

    @Column(name = "print_name", length = 150)
    private String printName;

    @Column(name = "excel_data", columnDefinition = "bytea")
    private byte[] excelData;

    @Column(name = "pdf_data", columnDefinition = "bytea")
    private byte[] pdfData;

    @Column(name = "correction_of_id")
    private UUID correctionOfId;

    @Column(name = "correction_number")
    private Integer correctionNumber;

    @Column(name = "superseded")
    private Boolean superseded;

    @Column(name = "correction_reason", columnDefinition = "TEXT")
    private String correctionReason;

    @Column(name = "corrected_by_name", length = 150)
    private String correctedByName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAirlineId() { return airlineId; }
    public void setAirlineId(UUID airlineId) { this.airlineId = airlineId; }
    public UUID getMawbId() { return mawbId; }
    public void setMawbId(UUID mawbId) { this.mawbId = mawbId; }
    public String getMawbNumber() { return mawbNumber; }
    public void setMawbNumber(String mawbNumber) { this.mawbNumber = mawbNumber; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getGatewayCfs() { return gatewayCfs; }
    public void setGatewayCfs(String gatewayCfs) { this.gatewayCfs = gatewayCfs; }
    public String getShipperName() { return shipperName; }
    public void setShipperName(String shipperName) { this.shipperName = shipperName; }
    public String getConsigneeName() { return consigneeName; }
    public void setConsigneeName(String consigneeName) { this.consigneeName = consigneeName; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Integer getAwbReportedPieces() { return awbReportedPieces; }
    public void setAwbReportedPieces(Integer awbReportedPieces) { this.awbReportedPieces = awbReportedPieces; }
    public BigDecimal getMawbWeightGreatest() { return mawbWeightGreatest; }
    public void setMawbWeightGreatest(BigDecimal mawbWeightGreatest) { this.mawbWeightGreatest = mawbWeightGreatest; }
    public BigDecimal getShipperReportedWeight() { return shipperReportedWeight; }
    public void setShipperReportedWeight(BigDecimal shipperReportedWeight) { this.shipperReportedWeight = shipperReportedWeight; }
    public OffsetDateTime getStartDatetime() { return startDatetime; }
    public void setStartDatetime(OffsetDateTime startDatetime) { this.startDatetime = startDatetime; }
    public OffsetDateTime getReceiptDate() { return receiptDate; }
    public void setReceiptDate(OffsetDateTime receiptDate) { this.receiptDate = receiptDate; }
    public Boolean getCashOnly() { return cashOnly; }
    public void setCashOnly(Boolean cashOnly) { this.cashOnly = cashOnly; }
    public Boolean getBookedInAcoms() { return bookedInAcoms; }
    public void setBookedInAcoms(Boolean bookedInAcoms) { this.bookedInAcoms = bookedInAcoms; }
    public Boolean getDocsProvided() { return docsProvided; }
    public void setDocsProvided(Boolean docsProvided) { this.docsProvided = docsProvided; }
    public Boolean getCustomsCompleted() { return customsCompleted; }
    public void setCustomsCompleted(Boolean customsCompleted) { this.customsCompleted = customsCompleted; }
    public Boolean getPreBuilt() { return preBuilt; }
    public void setPreBuilt(Boolean preBuilt) { this.preBuilt = preBuilt; }
    public Boolean getLooseTender() { return looseTender; }
    public void setLooseTender(Boolean looseTender) { this.looseTender = looseTender; }
    public Integer getPieceCount() { return pieceCount; }
    public void setPieceCount(Integer pieceCount) { this.pieceCount = pieceCount; }
    public Integer getDimFactorDom() { return dimFactorDom; }
    public void setDimFactorDom(Integer dimFactorDom) { this.dimFactorDom = dimFactorDom; }
    public Integer getDimFactorIntl() { return dimFactorIntl; }
    public void setDimFactorIntl(Integer dimFactorIntl) { this.dimFactorIntl = dimFactorIntl; }
    public BigDecimal getActualWeightLbs() { return actualWeightLbs; }
    public void setActualWeightLbs(BigDecimal actualWeightLbs) { this.actualWeightLbs = actualWeightLbs; }
    public BigDecimal getActualWeightKg() { return actualWeightKg; }
    public void setActualWeightKg(BigDecimal actualWeightKg) { this.actualWeightKg = actualWeightKg; }
    public BigDecimal getChargeableWeightLbs() { return chargeableWeightLbs; }
    public void setChargeableWeightLbs(BigDecimal chargeableWeightLbs) { this.chargeableWeightLbs = chargeableWeightLbs; }
    public BigDecimal getChargeableWeightKg() { return chargeableWeightKg; }
    public void setChargeableWeightKg(BigDecimal chargeableWeightKg) { this.chargeableWeightKg = chargeableWeightKg; }
    public String getShipperComment() { return shipperComment; }
    public void setShipperComment(String shipperComment) { this.shipperComment = shipperComment; }
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public String getDeliveredByName() { return deliveredByName; }
    public void setDeliveredByName(String deliveredByName) { this.deliveredByName = deliveredByName; }
    public String getDeliveredByIdNum() { return deliveredByIdNum; }
    public void setDeliveredByIdNum(String deliveredByIdNum) { this.deliveredByIdNum = deliveredByIdNum; }
    public String getDeliveredByIdDocUrl() { return deliveredByIdDocUrl; }
    public void setDeliveredByIdDocUrl(String deliveredByIdDocUrl) { this.deliveredByIdDocUrl = deliveredByIdDocUrl; }
    public String getDeliveredBySigUrl() { return deliveredBySigUrl; }
    public void setDeliveredBySigUrl(String deliveredBySigUrl) { this.deliveredBySigUrl = deliveredBySigUrl; }
    public String getReceivedByName() { return receivedByName; }
    public void setReceivedByName(String receivedByName) { this.receivedByName = receivedByName; }
    public String getReceivedByIdNum() { return receivedByIdNum; }
    public void setReceivedByIdNum(String receivedByIdNum) { this.receivedByIdNum = receivedByIdNum; }
    public String getReceivedByIdDocUrl() { return receivedByIdDocUrl; }
    public void setReceivedByIdDocUrl(String receivedByIdDocUrl) { this.receivedByIdDocUrl = receivedByIdDocUrl; }
    public String getReceivedBySigUrl() { return receivedBySigUrl; }
    public void setReceivedBySigUrl(String receivedBySigUrl) { this.receivedBySigUrl = receivedBySigUrl; }
    public String getBrokerName() { return brokerName; }
    public void setBrokerName(String brokerName) { this.brokerName = brokerName; }
    public String getBrokerIdNum() { return brokerIdNum; }
    public void setBrokerIdNum(String brokerIdNum) { this.brokerIdNum = brokerIdNum; }
    public String getBrokerIdDocUrl() { return brokerIdDocUrl; }
    public void setBrokerIdDocUrl(String brokerIdDocUrl) { this.brokerIdDocUrl = brokerIdDocUrl; }
    public String getBrokerSigUrl() { return brokerSigUrl; }
    public void setBrokerSigUrl(String brokerSigUrl) { this.brokerSigUrl = brokerSigUrl; }
    public String getReceiptDocUrl() { return receiptDocUrl; }
    public void setReceiptDocUrl(String receiptDocUrl) { this.receiptDocUrl = receiptDocUrl; }
    public String getDockSignature() { return dockSignature; }
    public void setDockSignature(String dockSignature) { this.dockSignature = dockSignature; }
    public String getSupportingDocs() { return supportingDocs; }
    public void setSupportingDocs(String supportingDocs) { this.supportingDocs = supportingDocs; }
    public UUID getHawbId() { return hawbId; }
    public void setHawbId(UUID hawbId) { this.hawbId = hawbId; }
    public String getPrintName() { return printName; }
    public void setPrintName(String printName) { this.printName = printName; }
    public byte[] getExcelData() { return excelData; }
    public void setExcelData(byte[] excelData) { this.excelData = excelData; }
    public byte[] getPdfData() { return pdfData; }
    public void setPdfData(byte[] pdfData) { this.pdfData = pdfData; }
    public UUID getCorrectionOfId() { return correctionOfId; }
    public void setCorrectionOfId(UUID correctionOfId) { this.correctionOfId = correctionOfId; }
    public Integer getCorrectionNumber() { return correctionNumber; }
    public void setCorrectionNumber(Integer correctionNumber) { this.correctionNumber = correctionNumber; }
    public Boolean getSuperseded() { return superseded; }
    public void setSuperseded(Boolean superseded) { this.superseded = superseded; }
    public String getCorrectionReason() { return correctionReason; }
    public void setCorrectionReason(String correctionReason) { this.correctionReason = correctionReason; }
    public String getCorrectedByName() { return correctedByName; }
    public void setCorrectedByName(String correctedByName) { this.correctedByName = correctedByName; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}