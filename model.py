import torch
model = torch.jit.load("eye_disease_classifier.pt")
example_input = torch.rand(1, 3, 256, 256)
print("Output shape:", model(example_input).shape)
