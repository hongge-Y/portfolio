/**
 * 新手引导的小宠目录与卡片文案 —— 纯展示数据，放 features 里，页面只管画。
 *
 * id 必须与后端保持一致（POST /v1/profile/guide 只接受 1~5），
 * 所以这里不做"新增一只宠物"的扩展点，改数量要同步后端校验。
 */

export const PETS = [
  {
    id: 1,
    name: '星光猫',
    emoji: '🐱',
    desc: '夜里也在替你看路',
    card: '星光猫 · 初见',
    cardDesc: '它会在你赶早八的时候蹲在课表上，提醒你第一节在哪间教室。'
  },
  {
    id: 2,
    name: '云朵犬',
    emoji: '🐶',
    desc: '闻得出哪节课最要紧',
    card: '云朵犬 · 初见',
    cardDesc: '作业和审批它都记着，看到待办会轻轻拽一下你的袖子。'
  },
  {
    id: 3,
    name: '萤火兔',
    emoji: '🐰',
    desc: '把零碎的光攒成一盏灯',
    card: '萤火兔 · 初见',
    cardDesc: '每一条红榜记录它都替你收着，学期末一起点亮成成长档案。'
  },
  {
    id: 4,
    name: '树莓熊',
    emoji: '🐻',
    desc: '擅长把复杂的事说简单',
    card: '树莓熊 · 初见',
    cardDesc: '流程不清楚的时候问它，它会用三句话说清该找谁、要交什么。'
  },
  {
    id: 5,
    name: '月影狐',
    emoji: '🦊',
    desc: '走得快，也不忘回头看',
    card: '月影狐 · 初见',
    cardDesc: '它陪你一起看完这学期的每一节课，也记得你请过的每一次假。'
  }
]

export function petById(id) {
  return PETS.find((p) => p.id === Number(id)) ?? null
}

/** 引导完成时的固定奖励（与后端 ProfileService.finishGuide 的 +100 光粒 / +1 礼盒对应） */
export const GUIDE_REWARD = { coin: 100, gift: 1 }
