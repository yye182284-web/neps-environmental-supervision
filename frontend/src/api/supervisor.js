import request from './request'
import { encryptPassword } from './security'

export async function supervisorLogin(data) {
  const encryptedPassword = await encryptPassword(data.password)
  return request.post('/supervisor/login', { telId: data.telId, encryptedPassword })
}

export async function supervisorRegister(data) {
  const encryptedPassword = await encryptPassword(data.password)
  const { password, ...profile } = data
  return request.post('/supervisor/register', { ...profile, encryptedPassword })
}
