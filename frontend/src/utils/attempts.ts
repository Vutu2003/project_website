import type { ExecutionAttempt } from '../types/execution'

export function orderedAttempts(attempts: ExecutionAttempt[]): ExecutionAttempt[] {
  return [...attempts].sort((a, b) => a.attemptNo - b.attemptNo || a.executionId - b.executionId)
}
