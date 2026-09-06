export const MM_PER_INCH = 25.4

export const SIZE_PRESETS = [
  { label: '2 x 1', w: 2, h: 1 },
  { label: '3 x 2', w: 3, h: 2 },
  { label: '4 x 3', w: 4, h: 3 },
  { label: '4 x 6', w: 4, h: 6 },
  { label: '6 x 4', w: 6, h: 4 },
]

export const FIELDS = {
  CARGO: [
    { key: 'AWB_NUMBER', label: 'AWB Number' },
    { key: 'SHIPPER_NAME', label: 'Shipper' },
    { key: 'CONSIGNEE_NAME', label: 'Consignee' },
    { key: 'ORIGIN', label: 'Origin' },
    { key: 'DESTINATION', label: 'Destination' },
    { key: 'PIECES', label: 'Piezas' },
    { key: 'WEIGHT_KG', label: 'Peso (Kg)' },
    { key: 'CHARGEABLE_KG', label: 'Peso Cobrable (Kg)' },
    { key: 'COMMODITY', label: 'Commodity' },
    { key: 'STATUS', label: 'Status' },
  ],
  PALLET: [
    { key: 'ULD_NUMBER', label: 'ULD Number' },
    { key: 'ULD_TYPE', label: 'Tipo ULD' },
    { key: 'POSITION', label: 'Posición' },
    { key: 'CONFIG', label: 'Config' },
    { key: 'SEAL', label: 'Sello' },
    { key: 'STATUS', label: 'Status' },
    { key: 'AIRLINE', label: 'Aerolínea (nombre)' },
    { key: 'AIRLINE_CODE', label: 'Aerolínea (código)' },
    { key: 'FLIGHT_NUMBER', label: 'Vuelo' },
    { key: 'FLIGHT_DATE', label: 'Fecha de vuelo' },
    { key: 'FLIGHT_ROUTE', label: 'Ruta (origen-destino)' },
    { key: 'GROSS_LBS', label: 'Peso Bruto (lbs)' },
    { key: 'TARE_LBS', label: 'Tara (lbs)' },
    { key: 'NET_LBS', label: 'Peso Neto (lbs)' },
    { key: 'GROSS_KG', label: 'Peso Bruto (kg)' },
    { key: 'TARE_KG', label: 'Tara (kg)' },
    { key: 'NET_KG', label: 'Peso Neto (kg)' },
    { key: 'PIECES', label: 'Piezas' },
    { key: 'MAWBS_COUNT', label: 'Nro. MAWBs' },
  ],
}

export const SAMPLE_DATA = {
  CARGO: {
    AWB_NUMBER: '125-12345678',
    SHIPPER_NAME: 'UPS SCS REP. DOM.',
    CONSIGNEE_NAME: 'UPS SCS MIAMI',
    ORIGIN: 'SDQ',
    DESTINATION: 'MIA',
    PIECES: '12',
    WEIGHT_KG: '450.5',
    CHARGEABLE_KG: '480.0',
    COMMODITY: 'DRY_CARGO',
    STATUS: 'RECEIVED',
  },
  PALLET: {
    ULD_NUMBER: 'PMC12345UP',
    ULD_TYPE: 'PMC',
    POSITION: 'L1',
    CONFIG: 'L',
    SEAL: 'SC-12345678',
    STATUS: 'SEALED',
    GROSS_LBS: '3500',
    TARE_LBS: '600',
    NET_LBS: '2900',
    GROSS_KG: '1587.6',
    TARE_KG: '272.2',
    NET_KG: '1315.4',
    AIRLINE: 'UPS SCS',
    AIRLINE_CODE: 'UPS',
    FLIGHT_NUMBER: '5X4321',
    FLIGHT_DATE: '2026-09-04',
    FLIGHT_ROUTE: 'SDQ-MIA',
    PIECES: '45',
    MAWBS_COUNT: '3',
  },
}

export function effectiveSize(template) {
  const w = Number(template.widthInches || 4)
  const h = Number(template.heightInches || 6)
  if (String(template.orientation || 'HORIZONTAL').toUpperCase() === 'VERTICAL') {
    return { w: h, h: w }
  }
  return { w, h }
}

export function mmToIn(v) { return v / MM_PER_INCH }
export function inToMm(v) { return v * MM_PER_INCH }

export function defaultElement(type, idx) {
  const base = { id: 'el' + Date.now() + '_' + idx, type }
  switch (type) {
    case 'text':
      return { ...base, x: 5, y: 5, w: 60, h: 6, fontSize: 6, bold: false, align: 'left', dataSource: 'TEXT', text: 'Texto' }
    case 'barcode':
      return { ...base, x: 5, y: 25, w: 60, h: 18, fontSize: 5, bold: false, align: 'center', dataSource: 'AWB_NUMBER', text: '', barcodeFormat: 'CODE128', barcodeHeight: 15 }
    case 'qrcode':
      return { ...base, x: 5, y: 25, w: 25, h: 25, fontSize: 5, bold: false, align: 'center', dataSource: 'AWB_NUMBER', text: '', barcodeFormat: 'QR', barcodeHeight: 25 }
    case 'line':
      return { ...base, x: 5, y: 20, w: 90, h: 0.4, fontSize: 0, bold: false, align: 'left', dataSource: 'TEXT', text: '' }
    case 'rect':
      return { ...base, x: 3, y: 3, w: 90, h: 40, fontSize: 0, bold: false, align: 'left', dataSource: 'TEXT', text: '' }
    default:
      return base
  }
}

export function resolveElementValue(el, type) {
  if (el.dataSource === 'TEXT' || !el.dataSource) return el.text || ''
  return SAMPLE_DATA[type]?.[el.dataSource] ?? ''
}
