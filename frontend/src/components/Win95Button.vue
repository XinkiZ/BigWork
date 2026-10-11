<!-- Win95 按钮 -->
<template>
  <button
    type="button"
    class="win-btn"
    :class="{ default: isDefault, small: size === 'small' }"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot>{{ label }}</slot>
  </button>
</template>

<script setup>
defineProps({
  label: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  isDefault: { type: Boolean, default: false },
  size: { type: String, default: 'normal' }
})
defineEmits(['click'])
</script>