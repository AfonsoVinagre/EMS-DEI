export default class SchoolDto {
  id?: number
  name?: string
  code?: string
  region?: string

  constructor(obj?: Partial<SchoolDto>) {
    Object.assign(this, obj)
  }
}