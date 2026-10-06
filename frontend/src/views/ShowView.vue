<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'
import { parkDate, parkTime, formatParkDateTime, PARK_ZONE_LABEL } from '../utils/parkTime'

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)

const dialog = ref(false)
const saving = ref(false)
const form = ref(blank())
function blank() {
  // showAtRaw：按园区（上海）时刻填写；后端也接受带偏移量的历史值
  return { showCode: '', showName: '', venue: '', capacity: '', booked: 0, showAtRaw: '' }
}

const reserveDialog = ref(false)
const reserving = ref(false)
const reserveForm = ref({ id: null, showName: '', seats: '' })

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listShows()
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function remaining(row) { return (row.capacity || 0) - (row.booked || 0) }

// 悬停可追溯：展示服务端解释口径与入库原始字面量（历史值可能带偏移量）
function rawTitle(row) {
  const base = `按园区固定时区 ${PARK_ZONE_LABEL} 解释；完整园区时刻：${formatParkDateTime(row)}`
  return row.showAtRaw ? `${base}；原始记录：${row.showAtRaw}` : base
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = { ...form.value, capacity: Number(form.value.capacity), booked: Number(form.value.booked || 0) }
    await api.createShow(d)
    tip.value = `演出 ${d.showCode} 已建档`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

function openReserve(row) {
  reserveForm.value = { id: row.id, showName: row.showName, seats: '' }
  error.value = ''; tip.value = ''; reserveDialog.value = true
}
async function doReserve() {
  reserving.value = true; error.value = ''
  try {
    await api.reserveShow(reserveForm.value.id, Number(reserveForm.value.seats))
    tip.value = `${reserveForm.value.showName} 预订成功`
    reserveDialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { reserving.value = false }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">园区演出 · 座位预订</span>
        <span class="panel-desc">预订不得超过容量，满座后会被拒绝；演出时刻按园区固定时区（{{ PARK_ZONE_LABEL }}）从早到晚排序</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">演出数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">总容量</div><div class="v">{{ rows.reduce((a, r) => a + (r.capacity || 0), 0) }}</div></div>
        <div class="stat"><div class="k">已订</div><div class="v">{{ rows.reduce((a, r) => a + (r.booked || 0), 0) }}</div></div>
      </div>

      <div class="toolbar">
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建演出</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="showCode" label="编号" width="100" />
        <el-table-column prop="showName" label="名称" min-width="140" />
        <el-table-column prop="venue" label="场地" width="120" />
        <el-table-column prop="capacity" label="容量" width="90" />
        <el-table-column prop="booked" label="已订" width="90" />
        <el-table-column label="余座" width="90">
          <template #default="{ row }"><span :style="{ color: remaining(row) <= 0 ? '#c0392b' : '#1e8e4e', fontWeight: 600 }">{{ remaining(row) }}</span></template>
        </el-table-column>
        <el-table-column label="园区日期" min-width="120">
          <template #default="{ row }">
            <span v-if="parkDate(row)" :title="rawTitle(row)">{{ parkDate(row) }}</span>
            <span v-else style="color:#909399">待排期</span>
          </template>
        </el-table-column>
        <el-table-column :label="`园区时间 · ${PARK_ZONE_LABEL}`" min-width="180">
          <template #default="{ row }">
            <span v-if="parkTime(row)" :title="rawTitle(row)">{{ parkTime(row) }}</span>
            <span v-else style="color:#909399">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="remaining(row) <= 0" @click="openReserve(row)">预订</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建演出" width="520px">
      <el-form :model="form" label-width="100px" class="dialog-form">
        <el-form-item label="演出编号"><el-input v-model="form.showCode" placeholder="如 S-008" /></el-form-item>
        <el-form-item label="演出名称"><el-input v-model="form.showName" /></el-form-item>
        <el-form-item label="场地"><el-input v-model="form.venue" /></el-form-item>
        <el-form-item label="容量"><el-input v-model="form.capacity" type="number" /></el-form-item>
        <el-form-item label="已订座位"><el-input v-model="form.booked" type="number" /></el-form-item>
        <el-form-item label="演出时刻">
          <el-date-picker v-model="form.showAtRaw" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                          placeholder="按园区（上海）时刻选择；留空为未排期" style="width:100%" />
          <div style="color:#909399;font-size:12px;line-height:1.6">
            填写值即园区挂钟时刻（{{ PARK_ZONE_LABEL }}），由服务端统一解释；历史带偏移量的值（如 2028-03-02T23:30:00Z）也可直接提交。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">建档</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reserveDialog" :title="`座位预订 · ${reserveForm.showName}`" width="420px">
      <el-form label-width="100px" class="dialog-form">
        <el-form-item label="预订座位数"><el-input v-model="reserveForm.seats" type="number" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reserveDialog = false">取消</el-button>
        <el-button type="primary" :loading="reserving" @click="doReserve">确认预订</el-button>
      </template>
    </el-dialog>
  </div>
</template>
