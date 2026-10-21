
Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.Rate = 0
$synth.Volume = 100
$synth.SetOutputToWaveFile('scratch\hello_from_itantra.wav')
$synth.Speak('Hello from iTantra')
$synth.Dispose()
