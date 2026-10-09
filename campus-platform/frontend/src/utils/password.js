/**
 * 密码强度前端校验，与后端 @PasswordPolicy 保持同一套规则：
 * 8-20 位，大写/小写/数字/特殊字符四类至少含三类。
 * 后端仍是最终裁决，此处仅作即时反馈。
 */

/** 四类字符至少满足三类的强度校验（Element Plus validator 用法） */
export function validatePasswordStrength(rule, value, callback) {
  if (!value) return callback()
  let classes = 0
  if (/[a-z]/.test(value)) classes++
  if (/[A-Z]/.test(value)) classes++
  if (/[0-9]/.test(value)) classes++
  if (/[^a-zA-Z0-9]/.test(value)) classes++
  if (value.length < 8 || value.length > 20) {
    callback(new Error('密码长度8-20位'))
  } else if (classes < 3) {
    callback(new Error('需含大写字母、小写字母、数字、特殊字符中的至少三类'))
  } else {
    callback()
  }
}
