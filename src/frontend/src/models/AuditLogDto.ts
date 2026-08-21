export default interface AuditLogDto {
  id?: number
  occurredAt?: string
  actorId?: number | null
  actorEmail?: string | null
  actorName?: string | null
  actorType?: string | null
  action?: string
  targetType?: string | null
  targetId?: number | null
  description?: string
  prevValue?: string | null
  newValue?: string | null
  prevHash?: string
  entryHash?: string
}