export default class SystemStateDto {
  examsLocked?: boolean

  constructor(obj?: Partial<SystemStateDto>) {
    Object.assign(this, obj)
  }
}
