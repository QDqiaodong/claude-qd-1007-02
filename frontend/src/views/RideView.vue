<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const statusFilter = ref('')
const statuses = ['开放', '检修', '关闭']

const dialog = ref(false)
const saving = ref(false)
const form = ref(blank())
function blank() {
  return { rideCode: '', rideName: '', minHeightCm: '', maxAge: '', status: '开放' }
}

const boardDialog = ref(false)
const boarding = ref(false)
const boardForm = ref({ id: null, rideName: '', heightCm: '', age: '', memberId: null })
const boardResult = ref(null)
const boardError = ref('')
const memberOptions = ref([])
const memberOptionsLoading = ref(false)

const selectedOption = computed(() =>
  memberOptions.value.find(m => m.memberId === boardForm.value.memberId) || null)

function optionLabel(m) {
  if (m.validFrom) {
    return `${m.name}（${m.tier}）· ${m.passType} ${m.validFrom}~${m.validTo} · ${m.passStatus}`
  }
  return `${m.name}（${m.tier}）· ${m.passStatus}`
}
function passTagType(s) {
  return s === '当前有效' ? 'success' : s === '未生效' ? 'warning' : s === '已过期' ? 'danger' : 'info'
}

// ---------------- 检修安全复核与放行 ----------------
const checkItems = [
  { key: 'brakeOk', label: '制动系统', short: '制动' },
  { key: 'restraintOk', label: '约束装置', short: '约束' },
  { key: 'emergencyStopOk', label: '紧急停机', short: '停机' },
  { key: 'trialRunOk', label: '空载试运行', short: '空载' }
]

const inspDialog = ref(false)
const inspRide = ref(null)
const activeBatch = ref(null)
const batches = ref([])
const inspLoading = ref(false)
const acting = ref(false)
const editing = ref(false)
const inspError = ref('')
const inspTip = ref('')
const itemForm = ref({ brakeOk: null, restraintOk: null, emergencyStopOk: null, trialRunOk: null, remark: '' })
const inspectorName = ref(localStorage.getItem('inspectorName') || '')
const releaserName = ref(localStorage.getItem('releaserName') || '')

const allPassed = computed(() => checkItems.every(c => itemForm.value[c.key] === true))

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listRides(statusFilter.value ? { status: statusFilter.value } : {})
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = {
      ...form.value,
      minHeightCm: form.value.minHeightCm === '' ? null : Number(form.value.minHeightCm),
      maxAge: form.value.maxAge === '' ? null : Number(form.value.maxAge)
    }
    await api.createRide(d)
    tip.value = `设施 ${d.rideCode} 已建档`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function transfer(row, target) {
  error.value = ''; tip.value = ''
  try {
    await api.transferRide(row.id, target)
    tip.value = target === '检修'
      ? `${row.rideName} 已转入检修，本次安全复核批次已生成`
      : `${row.rideName} 已转为「${target}」`
    await load()
  } catch (e) { error.value = e.message }
}

async function openBoard(row) {
  boardForm.value = { id: row.id, rideName: row.rideName, heightCm: '', age: '', memberId: null }
  boardResult.value = null
  boardError.value = ''
  boardDialog.value = true
  error.value = ''; tip.value = ''
  // 每次打开实时拉取会员与卡片有效期/状态，续期后无需刷新页面即可在本次核验生效
  memberOptionsLoading.value = true
  try {
    memberOptions.value = (await api.listBoardingOptions()) || []
  } catch (e) {
    memberOptions.value = []
    boardError.value = e.message
  } finally { memberOptionsLoading.value = false }
}
async function doBoard() {
  if (boarding.value) return // 防重复点击：核验进行中直接忽略
  boarding.value = true; boardError.value = ''
  // clearable 清空时 el-select 可能给出空串，统一归一为 null（散客）
  const memberId = boardForm.value.memberId ? Number(boardForm.value.memberId) : null
  try {
    const result = await api.boardRide(
      boardForm.value.id,
      boardForm.value.heightCm === '' ? null : Number(boardForm.value.heightCm),
      boardForm.value.age === '' ? null : Number(boardForm.value.age),
      memberId
    )
    boardResult.value = result
  } catch (e) {
    // 拒绝时后端返回逐项核验明细；无明细（如设施不存在）退回统一文案
    boardResult.value = e.data || null
    boardError.value = e.message
  } finally { boarding.value = false }
}

// ---------------- 复核对话框 ----------------

async function openInspect(row) {
  inspRide.value = row
  activeBatch.value = null
  batches.value = []
  editing.value = false
  inspError.value = ''; inspTip.value = ''
  inspDialog.value = true
  await refreshBatches()
}

async function refreshBatches() {
  inspLoading.value = true
  try {
    const [active, history] = await Promise.all([
      api.activeInspection(inspRide.value.id),
      api.listInspections(inspRide.value.id)
    ])
    activeBatch.value = active || null
    batches.value = history || []
    if (activeBatch.value && activeBatch.value.status === '填写中') {
      fillForm(activeBatch.value)
      editing.value = false
    }
  } catch (e) { inspError.value = e.message } finally { inspLoading.value = false }
}

function fillForm(b) {
  itemForm.value = {
    brakeOk: b.brakeOk, restraintOk: b.restraintOk,
    emergencyStopOk: b.emergencyStopOk, trialRunOk: b.trialRunOk,
    remark: b.remark || ''
  }
}

async function ensureBatch() {
  acting.value = true; inspError.value = ''
  try {
    await api.ensureInspection(inspRide.value.id)
    await refreshBatches()
  } catch (e) { inspError.value = e.message } finally { acting.value = false }
}

function startEdit() {
  fillForm(activeBatch.value)
  editing.value = true
  inspTip.value = ''
  inspError.value = ''
}

async function saveItems(silent) {
  const res = await api.saveInspectionItems(activeBatch.value.id, itemForm.value)
  if (!silent) inspTip.value = '复核项已保存'
  return res
}

async function doSaveItems() {
  acting.value = true; inspError.value = ''; inspTip.value = ''
  try {
    await saveItems(false)
    editing.value = false
    await refreshBatches()
  } catch (e) { inspError.value = e.message } finally { acting.value = false }
}

async function submitBatch() {
  inspError.value = ''; inspTip.value = ''
  if (!allPassed.value) {
    inspError.value = '所有必检项（制动系统、约束装置、紧急停机、空载试运行）合格后才能提交'
    return
  }
  if (!inspectorName.value.trim()) {
    inspError.value = '请填写安全员（提交人）姓名'
    return
  }
  acting.value = true
  try {
    // 退回修改场景：保存会使原批次失效并生成新批次，须用返回的最新批次提交
    const saved = await saveItems(true)
    await api.submitInspection(saved.id, inspectorName.value.trim())
    localStorage.setItem('inspectorName', inspectorName.value.trim())
    inspTip.value = '复核已提交，待负责人放行（提交人不能担任放行人）'
    editing.value = false
    await refreshBatches()
  } catch (e) {
    inspError.value = e.message
    await refreshBatches()
  } finally { acting.value = false }
}

async function releaseBatch() {
  inspError.value = ''; inspTip.value = ''
  if (!releaserName.value.trim()) {
    inspError.value = '请填写负责人（放行人）姓名'
    return
  }
  if (activeBatch.value && releaserName.value.trim() === activeBatch.value.submittedBy) {
    inspError.value = '提交人不能担任最终放行人，安全员与放行人须分离'
    return
  }
  acting.value = true
  try {
    await api.releaseInspection(activeBatch.value.id, releaserName.value.trim())
    localStorage.setItem('releaserName', releaserName.value.trim())
    tip.value = `${inspRide.value.rideName} 复核通过，已恢复开放，乘坐校验即时按最新限制生效`
    inspDialog.value = false
    await load()
  } catch (e) {
    // 未获得放行权（他人已放行/批次已失效等）：展示设施当前状态，绝不在本地回滚
    inspError.value = e.message
    await load()
    syncInspRide()
    await refreshBatches()
  } finally { acting.value = false }
}

// 放行冲突/状态变化后，用主列表的最新数据刷新对话框头部的设施状态
function syncInspRide() {
  const fresh = rows.value.find(r => r.id === inspRide.value?.id)
  if (fresh) inspRide.value = fresh
}

function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '—' }
function fmtOk(v) { return v === true ? '合格' : v === false ? '不合格' : '未检' }
function okClass(v) { return v === true ? 'ok-pass' : v === false ? 'ok-fail' : 'ok-none' }
function fmtItems(b) {
  return checkItems.map(c => `${c.short}${b[c.key] === true ? '✓' : b[c.key] === false ? '✗' : '–'}`).join(' ')
}
function batchTagType(s) {
  return s === '已放行' ? 'success' : s === '待放行' ? 'warning' : s === '已失效' ? 'danger' : 'info'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">游乐设施 · 乘坐校验与安全复核</span>
        <span class="panel-desc">乘坐核验：会员身份—年卡有效期—设施状态/身高/年龄一次完成；未选会员按散客核验，有效卡也不能绕过设施限制；检修后恢复开放须完成安全复核并由负责人放行</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">设施数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">开放</div><div class="v">{{ rows.filter(r => r.status === '开放').length }}</div></div>
        <div class="stat"><div class="k">检修</div><div class="v">{{ rows.filter(r => r.status === '检修').length }}</div></div>
        <div class="stat"><div class="k">关闭</div><div class="v">{{ rows.filter(r => r.status === '关闭').length }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px" @change="load">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建设施</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="rideCode" label="编号" width="100" />
        <el-table-column prop="rideName" label="名称" min-width="130" />
        <el-table-column prop="minHeightCm" label="最低身高(cm)" width="120" />
        <el-table-column prop="maxAge" label="最大年龄" width="100" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '开放' ? 'success' : row.status === '检修' ? 'warning' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openBoard(row)">乘坐校验</el-button>
            <template v-if="row.status === '检修'">
              <el-button size="small" type="warning" @click="openInspect(row)">安全复核 / 放行</el-button>
            </template>
            <template v-else>
              <el-button size="small" @click="transfer(row, '检修')">检修</el-button>
              <el-button size="small" :disabled="row.status === '关闭'" @click="transfer(row, '关闭')">关闭</el-button>
              <el-button size="small" type="success" plain :disabled="row.status !== '关闭'" @click="transfer(row, '开放')">开放</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建设施" width="520px">
      <el-form :model="form" label-width="110px" class="dialog-form">
        <el-form-item label="设施编号"><el-input v-model="form.rideCode" placeholder="如 R-009" /></el-form-item>
        <el-form-item label="设施名称"><el-input v-model="form.rideName" /></el-form-item>
        <el-form-item label="最低身高(cm)"><el-input v-model="form.minHeightCm" type="number" placeholder="不限留空" /></el-form-item>
        <el-form-item label="最大年龄"><el-input v-model="form.maxAge" type="number" placeholder="不限留空" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio v-for="s in statuses" :key="s" :value="s">{{ s }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">建档</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="boardDialog" :title="`乘坐核验 · ${boardForm.rideName}`" width="560px">
      <el-form label-width="110px" class="dialog-form">
        <el-form-item label="选择会员">
          <el-select
            v-model="boardForm.memberId"
            placeholder="不选会员则按散客核验（身高/年龄/设施状态）"
            clearable filterable
            :loading="memberOptionsLoading"
            style="width: 100%"
            @change="boardResult = null; boardError = ''"
          >
            <el-option
              v-for="m in memberOptions"
              :key="m.memberId"
              :label="optionLabel(m)"
              :value="m.memberId"
            >
              <div class="opt-line">
                <span>{{ m.name }}（{{ m.tier }}）</span>
                <el-tag size="small" :type="passTagType(m.passStatus)">{{ m.passStatus }}</el-tag>
              </div>
              <div class="opt-sub">
                <template v-if="m.validFrom">{{ m.passType }} {{ m.validFrom }} ~ {{ m.validTo }}</template>
                <template v-else>尚未办理年卡/季卡（会员等级不等于有效卡）</template>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedOption && !selectedOption.validFrom" label=" ">
          <span class="warn-note">该会员尚未办卡：选择后核验会因「未办卡」被拒绝，可清空选择改用散客。</span>
        </el-form-item>
        <el-form-item label="游客身高(cm)"><el-input v-model="boardForm.heightCm" type="number" /></el-form-item>
        <el-form-item label="游客年龄"><el-input v-model="boardForm.age" type="number" /></el-form-item>
      </el-form>

      <!-- 可解释结果：会员身份 — 卡片有效期 — 设施限制逐项展示，文案与接口完全一致 -->
      <div v-if="boardResult && boardResult.checks" class="board-result">
        <div :class="boardResult.allowed ? 'board-head ok' : 'board-head fail'">
          {{ boardResult.allowed ? '✅ ' : '⛔ ' }}{{ boardResult.reason }}
        </div>
        <ul class="check-list">
          <li v-for="c in boardResult.checks" :key="c.key" :class="c.skipped ? 'skip' : c.pass ? 'pass' : 'fail'">
            <span class="ci-icon">{{ c.skipped ? '—' : c.pass ? '✓' : '✗' }}</span>
            <div class="ci-body">
              <div class="ci-label">{{ c.label }}</div>
              <div class="ci-detail">{{ c.detail }}</div>
              <div v-if="!c.pass && !c.skipped && c.message" class="ci-msg">{{ c.message }}</div>
            </div>
          </li>
        </ul>
        <div v-if="!boardResult.allowed" class="board-reason">拒绝原因：{{ boardResult.reason }}</div>
      </div>
      <div v-else-if="boardError" class="err">{{ boardError }}</div>

      <template #footer>
        <el-button @click="boardDialog = false">关闭</el-button>
        <el-button type="primary" :loading="boarding" @click="doBoard">核验</el-button>
      </template>
    </el-dialog>

    <!-- 检修安全复核与放行 -->
    <el-dialog v-model="inspDialog" :title="`安全复核 · ${inspRide ? inspRide.rideName : ''}`" width="720px">
      <div v-if="inspRide" v-loading="inspLoading">
        <div class="insp-head">
          <span>设施状态：
            <el-tag :type="inspRide.status === '开放' ? 'success' : inspRide.status === '检修' ? 'warning' : 'info'">
              {{ inspRide.status }}
            </el-tag>
          </span>
          <span class="insp-limit">当前限制：身高 ≥ {{ inspRide.minHeightCm ?? '不限' }}cm ／ 年龄 ≤ {{ inspRide.maxAge ?? '不限' }} 岁</span>
        </div>

        <div v-if="inspError" class="err">{{ inspError }}</div>
        <div v-if="inspTip" class="ok-tip">{{ inspTip }}</div>

        <!-- 无进行中批次 -->
        <div v-if="!activeBatch" class="insp-empty">
          <p>当前没有进行中的复核批次。</p>
          <el-button v-if="inspRide.status === '检修'" type="warning" :loading="acting" @click="ensureBatch">
            发起本次安全复核批次
          </el-button>
        </div>

        <!-- 进行中批次 -->
        <div v-else class="insp-card">
          <div class="insp-card-head">
            <span class="insp-no">{{ activeBatch.batchNo }}</span>
            <el-tag :type="batchTagType(activeBatch.status)">{{ activeBatch.status }}</el-tag>
          </div>

          <!-- 填写中 / 退回修改：可编辑 -->
          <template v-if="activeBatch.status === '填写中' || editing">
            <el-form label-width="110px" class="dialog-form">
              <el-form-item v-for="c in checkItems" :key="c.key" :label="c.label">
                <el-radio-group v-model="itemForm[c.key]">
                  <el-radio :value="true">合格</el-radio>
                  <el-radio :value="false">不合格</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="备注"><el-input v-model="itemForm.remark" placeholder="选填" /></el-form-item>
              <el-form-item label="安全员">
                <el-input v-model="inspectorName" placeholder="提交人姓名（提交后不能担任放行人）" style="width: 280px" />
              </el-form-item>
            </el-form>
            <div class="insp-actions">
              <el-button :loading="acting" @click="doSaveItems">保存记录</el-button>
              <el-button type="primary" :loading="acting" :disabled="!allPassed" @click="submitBatch">提交复核</el-button>
            </div>
            <div class="insp-note">提交条件：四项必检全部「合格」；提交后再修改复核项，原批次将标记失效（历史保留）。</div>
          </template>

          <!-- 待放行：只读 + 负责人放行 -->
          <template v-else-if="activeBatch.status === '待放行'">
            <div class="insp-items">
              <span v-for="c in checkItems" :key="c.key" class="insp-item">
                {{ c.label }}<b :class="okClass(activeBatch[c.key])">{{ fmtOk(activeBatch[c.key]) }}</b>
              </span>
            </div>
            <div class="insp-meta">
              提交人：{{ activeBatch.submittedBy }} · {{ fmtTime(activeBatch.submittedAt) }}<br>
              提交时限制快照：身高 ≥ {{ activeBatch.snapshotMinHeightCm ?? '不限' }}cm ／ 年龄 ≤ {{ activeBatch.snapshotMaxAge ?? '不限' }} 岁
              <span v-if="activeBatch.remark">；备注：{{ activeBatch.remark }}</span>
            </div>
            <el-form label-width="110px" class="dialog-form" style="margin-top: 10px">
              <el-form-item label="负责人">
                <el-input v-model="releaserName" placeholder="放行人姓名（不能是提交人）" style="width: 280px" />
              </el-form-item>
            </el-form>
            <div class="insp-actions">
              <el-button :loading="acting" @click="startEdit">退回修改复核项</el-button>
              <el-button type="success" :loading="acting" :disabled="!releaserName.trim()" @click="releaseBatch">
                确认恢复开放
              </el-button>
            </div>
            <div class="insp-note">放行条件：设施仍处于检修、复核内容未过期、放行人 ≠ 提交人；并发或重复点击只生效一次。</div>
          </template>
        </div>

        <!-- 历史批次：永久可追溯 -->
        <div v-if="batches.length" class="insp-history">
          <div class="insp-history-title">复核历史（失效批次保留可追溯，不会被新批次覆盖）</div>
          <el-table :data="batches" size="small" border>
            <el-table-column prop="batchNo" label="批次号" width="150" />
            <el-table-column label="状态" width="76">
              <template #default="{ row }">
                <el-tag size="small" :type="batchTagType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="检查结果" width="130">
              <template #default="{ row }">{{ fmtItems(row) }}</template>
            </el-table-column>
            <el-table-column prop="submittedBy" label="提交人" width="76">
              <template #default="{ row }">{{ row.submittedBy || '—' }}</template>
            </el-table-column>
            <el-table-column prop="releasedBy" label="放行人" width="76">
              <template #default="{ row }">{{ row.releasedBy || '—' }}</template>
            </el-table-column>
            <el-table-column label="失效原因 / 时间" min-width="150">
              <template #default="{ row }">
                <span v-if="row.status === '已失效'">{{ row.invalidateReason }}</span>
                <span v-else-if="row.releasedAt">放行于 {{ fmtTime(row.releasedAt) }}</span>
                <span v-else>—</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.insp-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}
.insp-limit {
  font-size: 12px;
  color: var(--muted);
}
.insp-empty {
  text-align: center;
  padding: 18px 0;
  color: var(--muted);
}
.insp-card {
  border: 1px solid #ffe49a;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 14px;
  background: #fffdf4;
}
.insp-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.insp-no {
  font-weight: 700;
}
.insp-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.insp-note {
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
}
.insp-items {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.insp-item b {
  margin-left: 6px;
}
.ok-pass { color: #1e8e4e; }
.ok-fail { color: #c0392b; }
.ok-none { color: var(--muted); }
.insp-meta {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.7;
}
.insp-history-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 8px;
}
.opt-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.opt-sub {
  font-size: 12px;
  color: var(--muted);
  margin-top: 2px;
}
.warn-note {
  font-size: 12px;
  color: #b36b00;
}
.board-result {
  margin-top: 4px;
  border-top: 1px dashed #e5d9b8;
  padding-top: 12px;
}
.board-head {
  font-weight: 700;
  padding: 8px 12px;
  border-radius: 8px;
  margin-bottom: 10px;
}
.board-head.ok {
  background: #eaf8ef;
  color: #1e8e4e;
}
.board-head.fail {
  background: #fdecea;
  color: #c0392b;
}
.check-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.check-list li {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #faf6ea;
}
.check-list li.pass { background: #f1faf3; }
.check-list li.fail { background: #fdf1ef; }
.check-list li.skip { background: #f5f5f5; opacity: 0.85; }
.ci-icon {
  width: 18px;
  font-weight: 700;
}
li.pass .ci-icon { color: #1e8e4e; }
li.fail .ci-icon { color: #c0392b; }
li.skip .ci-icon { color: var(--muted); }
.ci-label { font-weight: 600; font-size: 13px; }
.ci-detail { font-size: 12px; color: var(--muted); margin-top: 2px; }
.ci-msg { font-size: 12px; color: #c0392b; margin-top: 2px; }
.board-reason {
  margin-top: 10px;
  font-size: 13px;
  font-weight: 700;
  color: #c0392b;
}
</style>
