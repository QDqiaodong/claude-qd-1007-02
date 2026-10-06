<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)

const dialog = ref(false)
const saving = ref(false)
const form = ref(blank())
function blank() {
  return { showCode: '', showName: '', venue: '', capacity: '', booked: 0, showAt: '' }
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
        <span class="panel-desc">预订不得超过容量，满座后会被拒绝</span>
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
        <el-table-column prop="showAt" label="演出时间" min-width="170" />
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
        <el-form-item label="演出时间"><el-date-picker v-model="form.showAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
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
