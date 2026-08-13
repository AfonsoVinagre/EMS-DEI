export default class FragmentDto {
  id?: number
  examId?: number
  pageNumber?: number
  subjectId?: number
  subjectName?: string
  imagePath?: string
  status?: string

  constructor(obj?: Partial<FragmentDto>) {
    Object.assign(this, obj)
  }
}
