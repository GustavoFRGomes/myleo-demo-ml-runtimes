Pod::Spec.new do |s|
  s.name             = 'onnxruntime-genai-c'
  s.version          = '0.17.0'
  s.summary          = 'ONNX Runtime GenAI C API (prebuilt iOS framework)'
  s.description      = 'C API for onnxruntime-genai. Ships the prebuilt onnxruntime-genai.xcframework ' \
                       'from the upstream release (which statically includes ONNX Runtime).'
  s.homepage         = 'https://github.com/microsoft/onnxruntime-genai'
  s.license          = { :type => 'MIT', :file => 'LICENSE' }
  s.author           = { 'ONNX Runtime' => 'onnxruntime@microsoft.com' }

  # CocoaPods downloads + verifies the archive; the xcframework ships inside it.
  s.source           = {
    :http => 'https://github.com/microsoft/onnxruntime-genai/releases/download/v0.17.0/' \
             'pod-archive-onnxruntime-genai-c-0.17.0.zip',
    :sha256 => 'cf82ad833b335e1e368cd100232a5539ec9b8cc739f2c886a4cc6a6f71e9aa6d',
  }

  s.ios.deployment_target = '17.0'
  s.osx.deployment_target = '12.0'

  s.vendored_frameworks = 'onnxruntime-genai.xcframework'
  s.static_framework    = true
  s.framework           = ['ImageIO', 'CoreGraphics']
  s.weak_framework      = ['CoreML']

  s.source_files        = 'Headers/*.h'
  s.preserve_paths      = ['LICENSE']
  s.libraries           = 'c++'

  s.pod_target_xcconfig = {
    'OTHER_CPLUSPLUSFLAGS' => '-fvisibility=hidden -fvisibility-inlines-hidden',
  }
end
