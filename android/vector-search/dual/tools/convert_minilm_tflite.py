#!/usr/bin/env python3
"""Convert all-MiniLM-L6-v2 to the fixed-length TFLite file this app bundles.

Both runtimes in this leaf run the *same* checkpoint
(`sentence-transformers/all-MiniLM-L6-v2`), not merely the same family. The ONNX
file is the official export; this script produces the equivalent TFLite from the
official `tf_model.h5` so the shared 384-d index is interchangeable.

Shape of the output (what `LiteRtEmbedder` expects):

- inputs: INT32 `[1, 128]` — `input_ids`, `attention_mask`, `token_type_ids`
  (resolved by tensor *name*, order varies per converter run)
- output: FLOAT `[1, 128, 384]` sequence (`last_hidden_state`)
- mean pooling + L2 normalisation happen in Kotlin, identically to the ONNX path
- fp32, TFLite built-ins only (no flex ops — the runtime has no flex delegate)

Setup (TF has no wheel for every Python; 3.11 works):

    python3.11 -m venv .venv-tflite
    .venv-tflite/bin/pip install tensorflow tf-keras "transformers==4.46.3"

`transformers` must stay on 4.x: 5.x dropped the TensorFlow model classes
(`TFAutoModel`).

Usage:

    .venv-tflite/bin/python tools/convert_minilm_tflite.py \
        app/src/main/assets/models/litert/embedding.tflite
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import tensorflow as tf
from transformers import TFAutoModel

MODEL_ID = "sentence-transformers/all-MiniLM-L6-v2"
SEQ_LEN = 128
HIDDEN = 384


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "output",
        type=Path,
        help="destination .tflite path (e.g. app/src/main/assets/models/litert/embedding.tflite)",
    )
    args = parser.parse_args()

    print(f"loading TF weights of {MODEL_ID} ...", flush=True)
    model = TFAutoModel.from_pretrained(MODEL_ID)

    spec = tf.TensorSpec([1, SEQ_LEN], tf.int32)

    @tf.function(input_signature=[spec, spec, spec], autograph=False)
    def encode(input_ids, attention_mask, token_type_ids):
        return model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            token_type_ids=token_type_ids,
            training=False,
        ).last_hidden_state

    converter = tf.lite.TFLiteConverter.from_concrete_functions(
        [encode.get_concrete_function()], encode
    )
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    converter.optimizations = []  # fp32, matching the ONNX reference
    blob = converter.convert()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(blob)
    print(f"wrote {args.output} ({len(blob)} bytes)", flush=True)

    # Smoke check: shapes/dtypes and an actual inference on a dummy sentence.
    interp = tf.lite.Interpreter(model_content=blob)
    interp.allocate_tensors()
    inputs = interp.get_input_details()
    output = interp.get_output_details()[0]
    print("inputs:", [(d["name"], np.dtype(d["dtype"]).name, d["shape"].tolist()) for d in inputs])
    print("output:", output["name"], np.dtype(output["dtype"]).name, output["shape"].tolist())

    ids = np.zeros((1, SEQ_LEN), np.int32)
    ids[0, :3] = (101, 2047, 102)  # [CLS] the [SEP]
    mask = np.zeros((1, SEQ_LEN), np.int32)
    mask[0, :3] = 1
    seg = np.zeros((1, SEQ_LEN), np.int32)
    for detail, value in zip(inputs, (ids, mask, seg)):
        interp.set_tensor(detail["index"], value)
    interp.invoke()
    pooled = interp.get_tensor(output["index"])[0, :3].mean(axis=0)
    pooled /= np.linalg.norm(pooled)
    assert pooled.shape == (HIDDEN,), pooled.shape
    print("pooled head:", np.round(pooled[:5], 5))


if __name__ == "__main__":
    main()
