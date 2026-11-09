const targetPetLabels = { CAT: '猫咪适用', DOG: '狗狗适用', ALL: '猫狗通用' }
const lifeStageLabels = {
  YOUNG: '幼年阶段',
  ADULT: '成年阶段',
  SENIOR: '老年阶段',
  ALL: '全阶段'
}

export function targetPetLabel(value) {
  return targetPetLabels[value] || '猫狗通用'
}

export function lifeStageLabel(value) {
  return lifeStageLabels[value] || '全阶段'
}

export function parseHealthTags(value) {
  return String(value || '')
    .split(/[,，;；、]/)
    .map((item) => item.trim())
    .filter(Boolean)
}
