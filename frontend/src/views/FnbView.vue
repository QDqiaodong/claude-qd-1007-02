<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const typeFilter = ref('')
const statusFilter = ref('')
const types = ['快餐', '正餐', '饮品']
const statuses = ['营业', '暂停', '关闭']

const dialog = ref(false)
const saving = ref(false)
const form = ref(blank())
function blank() {
  return { outletCode: '', name: '', type: '快餐', status: '营业' }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listFnb(
      (typeFilter.value || statusFilter.value)
        ? { type: typeFilter.value, status: statusFilter.value }
        : {}
    )
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    await api.createFnb({ ...form.value })
    tip.value = `网点 ${form.value.outletCode} 已建档`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function transfer(row, target) {
  error.value = ''; tip.value = ''
  try {
    await api.transferFnb(row.id, target)
    tip.value = `${row.name} 已转为「${target}」`
    await load()
  } catch (e) { error.value = e.message }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">餐饮网点 · 业态经营</span>
        <span class="panel-desc">快餐 / 正餐 / 饮品，状态在 营业 ↔ 暂停 ↔ 关闭 间流转</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">网点数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">营业</div><div class="v">{{ rows.filter(r => r.status === '营业').length }}</div></div>
        <div class="stat"><div class="k">暂停</div><div class="v">{{ rows.filter(r => r.status === '暂停').length }}</div></div>
        <div class="stat"><div class="k">关闭</div><div class="v">{{ rows.filter(r => r.status === '关闭').length }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="typeFilter" placeholder="全部业态" clearable style="width: 130px" @change="load">
          <el-option v-for="t in types" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px" @change="load">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建网点</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="outletCode" label="编号" width="100" />
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column prop="type" label="业态" width="90" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '营业' ? 'success' : row.status === '暂停' ? 'warning' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :disabled="row.status === '暂停'" @click="transfer(row, '暂停')">暂停</el-button>
            <el-button size="small" type="danger" plain :disabled="row.status === '关闭'" @click="transfer(row, '关闭')">关闭</el-button>
            <el-button size="small" type="success" plain :disabled="row.status === '营业'" @click="transfer(row, '营业')">营业</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建网点" width="520px">
      <el-form :model="form" label-width="100px" class="dialog-form">
        <el-form-item label="网点编号"><el-input v-model="form.outletCode" placeholder="如 F-007" /></el-form-item>
        <el-form-item label="网点名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="业态">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
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
  </div>
</template>
