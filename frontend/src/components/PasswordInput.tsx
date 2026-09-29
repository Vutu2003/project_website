import { useId, useState } from 'react'
import type { ComponentProps } from 'react'

type PasswordInputProps = Omit<ComponentProps<'input'>, 'type'> & {
  label: string
  helpText?: string
}

export function PasswordInput({ label, helpText, id, 'aria-describedby': describedBy, ...props }: PasswordInputProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const helpId = `${inputId}-help`
  const [visible, setVisible] = useState(false)
  const toggleLabel = visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'

  return <div className="password-field">
    <label htmlFor={inputId}>{label}</label>
    <div className="password-input">
      <input {...props} id={inputId} type={visible ? 'text' : 'password'}
        aria-describedby={[describedBy, helpText ? helpId : undefined].filter(Boolean).join(' ') || undefined} />
      <button className="password-toggle" type="button" aria-label={toggleLabel}
        aria-controls={inputId} aria-pressed={visible} disabled={props.disabled}
        onClick={() => setVisible(value => !value)}>{visible ? 'Ẩn' : 'Hiện'}</button>
    </div>
    {helpText && <small id={helpId}>{helpText}</small>}
  </div>
}
