Place a Gemma `.litertlm` model file in this directory and rename it to one of:

- gemma-3-4b-it.litertlm     (preferred for Phase 1)
- gemma-3-1b-it.litertlm     (fallback for lower-RAM devices)

Until a `.litertlm` file is present, the AI Lab screen will report the model as
NOT FOUND and inference will be unavailable.

How to obtain a compatible model:

1. Download a prebuilt `.litertlm` from the `litert-community` org on HuggingFace:
   https://huggingface.co/litert-community
   e.g. https://huggingface.co/litert-community/Gemma3-4B-IT

2. (Optional) Convert your own Gemma checkpoint with `litert-torch`:
   pip install litert-torch-nightly
   litert-torch export_hf \
     --model=google/gemma-3-4b-it \
     --output_dir=./gemma-3-4b-it

3. Push to the device (debug builds):
   adb push gemma-3-4b-it.litertlm /sdcard/Android/data/com.fitcheck.app/files/models/

   The app also looks inside this `assets/models/` directory on first run and
   will copy a bundled model into internal storage on demand.
