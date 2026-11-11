import { apiRequest, ApiError } from './client'

export interface RouterModel {
  id: string
  ownedBy: string | null
  shutdownDate: string | null
}

export async function listModels(
  apiKey: string,
  signal: AbortSignal,
): Promise<RouterModel[]> {
  const response = await apiRequest<unknown>('/v1/models', {
    headers: { Authorization: `Bearer ${apiKey}` },
    signal,
    cache: 'no-store',
  })
  if (
    !response ||
    typeof response !== 'object' ||
    !('object' in response) ||
    response.object !== 'list' ||
    !('data' in response) ||
    !Array.isArray(response.data)
  ) {
    throw new ApiError('invalid_response', '올바른 모델 목록 응답이 아닙니다.')
  }
  return response.data.map((model: unknown) => {
    if (
      !model ||
      typeof model !== 'object' ||
      !('id' in model) ||
      typeof model.id !== 'string' ||
      !model.id ||
      !('object' in model) ||
      model.object !== 'model'
    ) {
      throw new ApiError('invalid_response', '올바른 모델 정보가 아닙니다.')
    }
    return {
      id: model.id,
      ownedBy:
        'owned_by' in model && typeof model.owned_by === 'string'
          ? model.owned_by
          : null,
      shutdownDate:
        'shutdown_date' in model && typeof model.shutdown_date === 'string'
          ? model.shutdown_date
          : null,
    }
  })
}
