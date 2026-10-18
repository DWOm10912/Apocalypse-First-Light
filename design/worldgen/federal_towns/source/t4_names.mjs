// T4: the sixteen fixed town names (and a fictional pre-collapse population for the city limit signs), the street
// naming rules with each state's word list, Linden's streets, and the two sign kinds the framework adds.
import fs from 'fs';
import { sheet, text, lines, f, sign } from './svg.mjs';

const ROUTES = JSON.parse(fs.readFileSync(new URL('./data/r1_town_routes.json', import.meta.url), 'utf8')).towns;
// id, English (signs), Chinese, tier, state, population (fictional, before the collapse)
const TOWNS = [
  ['river_city', 'Bellhaven', '贝尔黑文', '城市', 'S1', 48600], ['industrial_city', 'Kessler', '凯斯勒', '城市', 'S4', 31200],
  ['port_city', 'Port Marais', '马雷港', '镇', 'S5', 14800], ['county_seat', 'Sutton', '萨顿（县城）', '镇', 'S2', 7900],
  ['valley_town', 'Linden', '林登', '镇', 'S1', 4300], ['desert_town', 'Mesa Roja', '梅萨罗哈', '镇', 'S6', 3600],
  ['mining_town', 'Cobalt', '科博尔特', '村', 'S3', 2100], ['port_town', 'Gull Harbor', '海鸥港', '村', 'S4', 1620],
  ['farm_town_north', 'Elmwood', '榆林镇', '村', 'S2', 1450], ['shore_town', 'Pelican Point', '鹈鹕角', '村', 'S5', 1380],
  ['farm_town_south', 'Redfield', '红田镇', '村', 'S2', 1180], ['mill_town', 'Birch Mills', '桦木磨坊', '村', 'S4', 1050],
  ['hill_village', 'Laurel Ridge', '月桂岭', '村', 'S1', 920], ['bayou_village', 'Bayou Noir', '黑水湾', '村', 'S5', 610],
  ['pass_village', 'Tamarack', '落叶松垭', '村', 'S3', 340], ['canyon_outpost', 'Fort Ocotillo', '奥科蒂约堡', '村', 'S6', 210],
];
const STATES = {
  S1: ['英式老州', 'Oak、Maple、Elm、Chestnut、Walnut、Cedar', 'High St、Church St、King St、Queen St、Mill St'],
  S2: ['中西部', 'Walnut、Cottonwood、Hickory、Locust、Sycamore', 'Courthouse Square、Court St、Grange St、Depot St、Prairie Ave'],
  S3: ['山区矿镇', 'Aspen、Spruce、Pine、Juniper、Fir', 'Silver St、Ore Ave、Lode St、Mine Rd、Summit St'],
  S4: ['五大湖工业', 'Birch、Ash、Beech、Poplar、Hemlock', 'Foundry St、Dock St、Harbor Rd、Lumber St、Lake Shore Dr'],
  S5: ['法语湾区', 'Magnolia、Cypress、Live Oak、Pecan、Willow', 'Royal St、Toulouse St、Dauphine St、Bienville St、Levee Rd'],
  S6: ['西南西班牙语', 'Mesquite、Saguaro、Palo Verde、Piñon、Yucca', 'Camino Real、Calle del Sol、Paseo、Arroyo Rd、Mesa Dr'],
};

export function namesSheet() {
  let b = '';
  // the towns
  const c = [40, 180, 300, 360, 470, 560];
  let y = 106;
  b += text(40, y - 20 + 8, '16 个城镇（名字和位置固定，每个世界都一样）', 'font-size="17" font-weight="700" fill="#23282b"');
  y += 16;
  b += `<rect x="30" y="${y - 18}" width="800" height="26" fill="#e3e6df"/>`;
  ['英文（牌面）', '中文', '档', '州（口音）', '人口*', '连着的路（r1）'].forEach((h, i) => { b += text(c[i], y, h, 'font-size="13" font-weight="700" fill="#23282b"'); });
  y += 26;
  for (const [id, en, zh, tier, st, pop] of TOWNS) {
    b += sign(c[0], y, en, 'font-size="16" fill="#1f5b3a"');
    b += text(c[1], y, zh, 'font-size="13.5" fill="#23282b"');
    b += text(c[2], y, tier, 'font-size="13.5" fill="#23282b"');
    b += text(c[3], y, `${st} ${STATES[st][0]}`, 'font-size="13" fill="#23282b"');
    b += text(c[4], y, pop.toLocaleString('en-US'), 'font-size="13.5" fill="#23282b"');
    b += text(c[5], y, (ROUTES[id] || []).join('、'), 'font-size="13" fill="#23282b"');
    y += 24;
  }
  b += text(40, y + 4, '* 人口是虚构的灾变前数字，写在城界牌上；城里的地块数按档定（T1），不按人口算。', 'font-size="12.5" fill="#6b7376"');

  // the rules
  const R = lines(880, 106, [
    ['街名规则（每座城镇按同一套规则起名，所以每个世界都一样）', 'font-size="17" font-weight="700" fill="#23282b"', 28],
    ['1. 镇的主轴叫 Main St：州道进镇那段、或沿州际的那条主街；州道的盾牌在镇里照挂'],
    ['2. 州际两边的平行路叫 Front St'],
    ['3. 和 Main St 平行的街，按离 Main St 的远近取本州的树名表（两侧分开取）'],
    ['4. 横街从镇的西头（南北向的镇从北头）起编号：1st St、2nd St、3rd St …'],
    ['　　城市里横街叫 Avenue（1st Ave …），平行的叫 Street'],
    ['5. 学校、教堂、法院、码头、工厂旁边的街，可以换成本州的特殊街名（每镇 2–3 条）'],
    ['6. 门牌号：横街编号 × 100（Main St 在 6th 和 7th 之间是 600 号段），以后纸条、任务可以写地址'],
    ['7. 街多街少随当地地形；但同一座镇里，同一位置的街永远叫同一个名字'],
  ], 23, 'font-size="13.5" fill="#23282b"');
  b += R.svg;
  // the word lists
  let wy = R.y + 18;
  b += `<rect x="870" y="${wy - 18}" width="900" height="26" fill="#e3e6df"/>`;
  ['州', '平行街（树名，按顺序）', '特殊街名'].forEach((h, i) => { b += text([880, 1000, 1330][i], wy, h, 'font-size="13" font-weight="700" fill="#23282b"'); });
  wy += 26;
  for (const [st, [acc, trees, special]] of Object.entries(STATES)) {
    b += text(880, wy, `${st} ${acc}`, 'font-size="13" fill="#23282b"');
    b += sign(1000, wy, trees.replace(/、/g, ' · '), 'font-size="13.5" fill="#23282b"');
    b += sign(1330, wy, special.replace(/、/g, ' · '), 'font-size="13.5" fill="#23282b"');
    wy += 24;
  }
  // Linden's streets
  const LL = lines(880, wy + 26, [
    ['Linden 的街（T2，S1 英式老州）', 'font-size="17" font-weight="700" fill="#23282b"', 28],
    ['· Main St（主街，C14）；Front St（F-40 两侧各一条）'],
    ['· 北侧平行街 Oak St、Maple St；南侧 Elm St'],
    ['· 横街 1st St – 11th St；6th St 上跨 F-40，是出口（EXIT 6th St / Downtown）'],
    ['· 3rd St 那个街区有教堂：按规则 5 可以叫 Church St（草稿先保留编号）'],
  ], 23, 'font-size="13.5" fill="#23282b"');
  b += LL.svg;

  // the signs: city limit (with population plaque and the 30 MPH it stands with) and a street corner blade pair
  const sy = Math.max(y + 60, LL.y + 30);
  b += text(40, sy, '框架加的两种牌（立在每条进镇的路上 / 每个路口）', 'font-size="17" font-weight="700" fill="#23282b"');
  const gx = 60, gy = sy + 24;
  // city limit sign
  b += `<rect x="${gx + 68}" y="${gy + 150}" width="7" height="120" fill="#8c9296"/>`;
  b += `<rect x="${gx}" y="${gy}" width="150" height="100" rx="8" fill="#1f6b45" stroke="#ffffff" stroke-width="3"/>`;
  b += sign(gx + 75, gy + 46, 'LINDEN', 'font-size="34" fill="#ffffff" text-anchor="middle"');
  b += sign(gx + 75, gy + 80, 'CITY LIMIT', 'font-size="19" fill="#ffffff" text-anchor="middle"');
  b += `<rect x="${gx + 15}" y="${gy + 108}" width="120" height="40" rx="5" fill="#1f6b45" stroke="#ffffff" stroke-width="2.5"/>`;
  b += sign(gx + 75, gy + 136, 'POP 4,300', 'font-size="20" fill="#ffffff" text-anchor="middle"');
  b += text(gx + 75, gy + 296, '城界牌 + 人口牌', 'font-size="13" fill="#23282b" text-anchor="middle"');
  // speed limit beside it
  const sx = gx + 200;
  b += `<rect x="${sx + 36}" y="${gy + 110}" width="7" height="160" fill="#8c9296"/>`;
  b += `<rect x="${sx}" y="${gy + 10}" width="80" height="100" rx="5" fill="#ffffff" stroke="#23282b" stroke-width="3"/>`;
  b += sign(sx + 40, gy + 38, 'SPEED', 'font-size="17" fill="#23282b" text-anchor="middle"') + sign(sx + 40, gy + 58, 'LIMIT', 'font-size="17" fill="#23282b" text-anchor="middle"') + sign(sx + 40, gy + 98, '30', 'font-size="38" fill="#23282b" text-anchor="middle"');
  b += text(sx + 40, gy + 296, '进镇限速 30 MPH', 'font-size="13" fill="#23282b" text-anchor="middle"');
  // street blades
  const bx = gx + 380;
  b += `<rect x="${bx + 96}" y="${gy + 20}" width="7" height="250" fill="#8c9296"/>`;
  b += `<rect x="${bx}" y="${gy + 10}" width="200" height="34" rx="4" fill="#1f6b45" stroke="#ffffff" stroke-width="2"/>`;
  b += sign(bx + 14, gy + 35, 'Main St', 'font-size="22" fill="#ffffff"') + sign(bx + 186, gy + 35, '600', 'font-size="13" fill="#ffffff" text-anchor="end"');
  b += `<rect x="${bx + 60}" y="${gy + 48}" width="80" height="34" rx="4" fill="#1f6b45" stroke="#ffffff" stroke-width="2" transform="rotate(0)"/>`;
  b += sign(bx + 100, gy + 72, '6th St', 'font-size="20" fill="#ffffff" text-anchor="middle"');
  b += text(bx + 100, gy + 296, '路口街名牌（两块交叉；小字是门牌段）', 'font-size="13" fill="#23282b" text-anchor="middle"');
  b += text(bx + 100, gy + 316, '（实物两块成 90°，这里摊平画）', 'font-size="11.5" fill="#6b7376" text-anchor="middle"');
  return sheet(1800, Math.max(1000, Math.ceil(gy + 360)), '地名和街名草稿：城镇名固定，街名按规则起，牌面英文', 'T4', b, '牌面字体是 Windows 的 Bahnschrift，只是示意');
}
