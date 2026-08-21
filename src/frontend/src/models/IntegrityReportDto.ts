export default interface IntegrityReportDto {
  intact: boolean
  brokenAtId?: number | null
  total: number
  message?: string
}