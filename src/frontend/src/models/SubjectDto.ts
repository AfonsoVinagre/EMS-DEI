export default class SubjectDto {
  id?: number
  name?: string
  code?: string

  constructor(obj?: Partial<SubjectDto>) {
    Object.assign(this, obj)
  }
}