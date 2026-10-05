export interface CatalogEquipment {
 equipment_id: number; equipment_code: string; equipment_name: string; department_name: string; quarters: string;
 contract_status?: string; warranty_start_date?: string | null; warranty_end_date?: string | null;
 warranty_status?: 'VALID' | 'EXPIRED' | 'UNRECORDED'; warranty_days_remaining?: number | null;
 contracts?: EquipmentContract[]; contracts_label?: string;
}
export interface EquipmentContract { contract_id: number; contract_code: string; contract_name: string; provider_id: number; provider_name: string; start_date: string; end_date: string; valid: boolean }
