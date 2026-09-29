/** Safe, intentionally written message for a local form validation failure. */
export class UserInputError extends Error {
  constructor(message: string) { super(message); this.name = 'UserInputError' }
}
