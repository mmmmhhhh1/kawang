import { describe, it, expect } from 'vitest'

import { mount } from '@vue/test-utils'
import App from '../App.vue'

describe('App', () => {
  it('mounts renders properly', () => {
    const wrapper = mount(App, {
      global: {
        stubs: {
          RouterView: {
            template: '<div>AI 会员商城</div>',
          },
        },
      },
    })
    expect(wrapper.text()).toContain('AI 会员商城')
  })
})
