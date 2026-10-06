import JSEncrypt from 'jsencrypt'
import request from './request'

let cachedPublicKey = ''

export async function preparePasswordEncryption() {
  if (cachedPublicKey) return cachedPublicKey
  const response = await request.get('/auth/public-key')
  cachedPublicKey = response.data.data
  return cachedPublicKey
}

export async function encryptPassword(password) {
  await preparePasswordEncryption()
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(cachedPublicKey)
  const encrypted = encryptor.encrypt(password)
  if (!encrypted) {
    cachedPublicKey = ''
    throw new Error('密码加密失败，请刷新页面后重试')
  }
  return encrypted
}
