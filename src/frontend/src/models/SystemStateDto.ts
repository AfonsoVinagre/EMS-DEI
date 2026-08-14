export default class SystemStateDto {
  examsLocked?: boolean
  distributed?: boolean

  constructor(obj?: Partial<SystemStateDto>) {
    Object.assign(this, obj)
  }
}
