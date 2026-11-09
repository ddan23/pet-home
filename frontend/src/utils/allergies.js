const LEGACY_ALLERGY_LABELS = Object.freeze({
  chicken: '鸡肉',
  beef: '牛肉',
  fish: '鱼类',
  grain: '谷物',
  dairy: '乳制品'
})

export const MAX_ALLERGY_COUNT = 20
export const MAX_ALLERGY_NAME_LENGTH = 50

export function formatAllergyLabel(value) {
  const text = String(value || '').trim()
  return LEGACY_ALLERGY_LABELS[text.toLowerCase()] || text
}

export function mergeAllergies(currentAllergies, inputText) {
  const allergies = Array.isArray(currentAllergies) ? [...currentAllergies] : []
  const existingLabels = new Set(allergies.map((item) => formatAllergyLabel(item).toLowerCase()))
  const candidates = String(inputText || '')
    .split(/[，,、;；\n]+/)
    .map((item) => formatAllergyLabel(item))
    .filter(Boolean)

  let duplicateCount = 0
  let tooLongCount = 0
  let limitCount = 0
  let addedCount = 0

  for (const candidate of candidates) {
    if (candidate.length > MAX_ALLERGY_NAME_LENGTH) {
      tooLongCount++
      continue
    }
    const normalized = candidate.toLowerCase()
    if (existingLabels.has(normalized)) {
      duplicateCount++
      continue
    }
    if (allergies.length >= MAX_ALLERGY_COUNT) {
      limitCount++
      continue
    }
    allergies.push(candidate)
    existingLabels.add(normalized)
    addedCount++
  }

  return {
    allergies,
    addedCount,
    duplicateCount,
    tooLongCount,
    limitCount
  }
}
