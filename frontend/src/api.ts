import axios from 'axios'

const messageTranslations: Array<[RegExp, string]> = [
  [/账号已停用，请联系管理员/g, 'This account is disabled. Contact an administrator.'],
  [/密码连续输错10次，账号已锁定，请联系管理员解锁/g, 'The account is locked after 10 failed sign-in attempts. Contact an administrator to unlock it.'],
  [/用户名或密码错误，还可尝试(\d+)次/g, 'Incorrect username or password. $1 attempts remaining.'],
  [/账号不可用，请联系管理员/g, 'This account is unavailable. Contact an administrator.'],
  [/当前密码不正确/g, 'The current password is incorrect.'],
  [/新密码不能与当前密码相同/g, 'The new password must differ from the current password.'],
  [/状态已变化，请刷新(?:后重试)?/g, 'The record has changed. Refresh and try again.'],
  [/已被其他人修改，请刷新(?:后重试)?/g, 'The record was changed by another user. Refresh and try again.'],
  [/只有未打印、未审核的草稿可以作废/g, 'Only an unprinted, unapproved draft can be voided.'],
  [/已作废单据不能打印/g, 'A voided document cannot be printed.'],
  [/至少填写一条支出明细/g, 'Add at least one expense line.'],
  [/支出金额必须大于0/g, 'The expense amount must be greater than zero.'],
  [/收支类别不存在或已停用/g, 'The income/expense category does not exist or is disabled.'],
  [/经手人不存在或已停用/g, 'The handler does not exist or is disabled.'],
  [/销售退货打印状态无效/g, 'Invalid sales-return print status.'],
  [/销售退货单不能重复选择/g, 'Do not select the same sales return more than once.'],
  [/客户不存在或已停用/g, 'The customer does not exist or is disabled.'],
  [/业务员不存在或已停用/g, 'The salesperson does not exist or is disabled.'],
  [/货品不存在、已停用或不可销售/g, 'The product does not exist, is disabled, or is not saleable.'],
  [/退货数量不能超过原销售数量/g, 'The return quantity cannot exceed the original sold quantity.'],
  [/请选择 CSV 文件/g, 'Select a CSV file.'],
  [/请选择 Excel 文件/g, 'Select an Excel file.'],
  [/仅支持 \.xlsx 或 \.xls 文件/g, 'Only .xlsx or .xls files are supported.'],
  [/无法读取 Excel，请确认文件未损坏且不是加密文件/g, 'Unable to read the Excel file. Make sure it is not damaged or encrypted.'],
  [/资料类型不存在/g, 'The master-data type does not exist.'],
  [/编号已存在，请更换编号/g, 'This code already exists. Enter a different code.'],
  [/该资料已被业务单据使用，不能删除；可以改为停用/g, 'This record is used by business documents and cannot be deleted. Disable it instead.'],
  [/排序方向无效/g, 'Invalid sort direction.'],
  [/排序字段无效/g, 'Invalid sort field.'],
  [/暂不支持该资料类型/g, 'This master-data type is not supported yet.'],
]

function translateMessage(value: unknown) {
  if (typeof value !== 'string') return value
  return messageTranslations.reduce((text, [pattern, replacement]) => text.replace(pattern, replacement), value)
}

export const api = axios.create({ baseURL: '/api/v1' })
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
api.interceptors.response.use(undefined, (error) => {
  const payload = error.response?.data
  if (payload && typeof payload === 'object') {
    for (const key of ['message', 'detail', 'error']) {
      if (key in payload) payload[key] = translateMessage(payload[key])
    }
  }
  if (error.response?.status === 401 && location.pathname !== '/login') {
    localStorage.removeItem('accessToken')
    location.href = '/login'
  }
  return Promise.reject(error)
})


