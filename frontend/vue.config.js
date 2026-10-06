const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  transpileDependencies: true,
  // Keep builds reliable on ordinary 8–16 GB developer laptops instead of spawning
  // one minifier worker per logical CPU.
  parallel: false,
  chainWebpack: config => {
    config.optimization.minimizer('terser').tap(args => {
      args[0].parallel = false
      return args
    })
  },
  devServer: {
    port: 8080,
    proxy: {
      '/api': {
        target: 'http://localhost:9000',
        changeOrigin: true,
        pathRewrite: { '^/api': '' }
      }
    }
  }
})
