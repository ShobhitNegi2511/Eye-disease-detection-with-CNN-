import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
import torch
import torch.nn as nn
from torchvision import datasets, transforms
from torch.utils.data import DataLoader
from torchvision.utils import make_grid
import multiprocessing
from tqdm import tqdm
from torchmetrics import Accuracy, ConfusionMatrix, Precision, Recall, F1Score

def load_data():
    t = transforms.Compose([
        transforms.ToTensor(),
        transforms.Resize((256, 256)),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])
    dataset = datasets.ImageFolder(root="dataset", transform=t)
    dataset.classes = ["Blepharitis", "Bulging_Eyes", "Cataract", "Chalazion", 
                      "Conjunctivitis", "Crossed_Eyes", "Diabetic_Retinopathy", 
                      "Eyelid_Drooping", "Glaucoma", "Jaundice", "Keratitis", 
                      "Normal", "Pterygium", "Stye", "Uveitis"]
    return dataset
    

def display_image(image, label):
    print(f"Label: {dataset.classes[label]}")
    plt.figure(figsize=(8, 8))
    plt.imshow(image.permute(1, 2, 0))
    plt.axis('off')
    plt.show()

def train_test_split(dataset, train_size, random_state=42):
    train_size = int(train_size * len(dataset))
    test_size = len(dataset) - train_size
    seed = torch.Generator().manual_seed(random_state)
    train_dataset, test_dataset = torch.utils.data.random_split(
        dataset, [train_size, test_size], generator=seed
    )
    
    return train_dataset, test_dataset

def show_batch(data_loader):
    for images, labels in data_loader:
        fig, ax = plt.subplots(figsize=(16, 12))
        ax.set_xticks([])
        ax.set_yticks([])
        ax.imshow(make_grid(images, nrow=8).permute(1, 2, 0))
        break

class CNN(nn.Module):
    def __init__(self, NUMBER_OF_CLASSES):
        super(CNN, self).__init__()
        self.conv_layers = nn.Sequential(
            nn.Conv2d(in_channels=3, out_channels=16, kernel_size=3, stride=2),
            nn.BatchNorm2d(16),
            nn.LeakyReLU(),
            nn.MaxPool2d(kernel_size=2, stride=2),
            
            nn.Conv2d(in_channels=16, out_channels=32, kernel_size=3, stride=2),
            nn.BatchNorm2d(32),
            nn.LeakyReLU(),
            nn.MaxPool2d(kernel_size=2, stride=2),
            
            nn.Conv2d(in_channels=32, out_channels=64, kernel_size=3, stride=2),
            nn.BatchNorm2d(64),
            nn.LeakyReLU(),
            nn.MaxPool2d(kernel_size=2, stride=2),
        )
        
        self.dense_layers = nn.Sequential(
            nn.Dropout(0.2),
            nn.Linear(64 * 3 * 3, 128),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(128, NUMBER_OF_CLASSES),
        )
    
    def forward(self, x):
        x = self.conv_layers(x)
        x = x.view(x.size(0), -1)
        x = self.dense_layers(x)
        
        return x

def batch_gd(model, criterion, optimizer, train_loader, test_loader, epochs):
    model.to(device)
    train_losses = np.zeros(epochs)
    test_losses = np.zeros(epochs)

    accuracy = Accuracy(task="multiclass", num_classes=NUMBER_OF_CLASSES).to(device)
    
    for epoch in range(epochs):
        # Training phase
        model.train()
        train_loss = []
        all_train_outputs = []
        all_train_targets = []
        
        for inputs, targets in tqdm(train_loader, desc=f'Training... Epoch: {epoch + 1}/{epochs}'):
            # move data to GPU
            inputs, targets = inputs.to(device), targets.to(device)

            # zero the parameter gradients
            optimizer.zero_grad()

            # Forward pass
            outputs = model(inputs)
            loss = criterion(outputs, targets)

            # Backward and optimize
            loss.backward()
            optimizer.step()

            train_loss.append(loss.item())
            
            # Store predictions and targets for accuracy calculation
            all_train_outputs.append(outputs.detach())
            all_train_targets.append(targets)

        # Get train loss
        train_loss = np.mean(train_loss)
        
        # Concatenate all batches for accuracy calculation
        all_train_outputs = torch.cat(all_train_outputs, dim=0)
        all_train_targets = torch.cat(all_train_targets, dim=0)
        
        # Train accuracy
        train_accuracy = accuracy(all_train_outputs, all_train_targets)

        # Validation phase
        model.eval()
        test_loss = []
        all_test_outputs = []
        all_test_targets = []
        
        with torch.no_grad():
            for inputs, targets in tqdm(test_loader, desc=f'Validating... Epoch: {epoch + 1}/{epochs}'):
                inputs, targets = inputs.to(device), targets.to(device)
                outputs = model(inputs)
                loss = criterion(outputs, targets)

                test_loss.append(loss.item())
                
                # Store predictions and targets for accuracy calculation
                all_test_outputs.append(outputs)
                all_test_targets.append(targets)

        # Get test loss
        test_loss = np.mean(test_loss)
        
        # Concatenate all batches for accuracy calculation
        all_test_outputs = torch.cat(all_test_outputs, dim=0)
        all_test_targets = torch.cat(all_test_targets, dim=0)
        
        # Test accuracy
        test_accuracy = accuracy(all_test_outputs, all_test_targets)

        # Save losses
        train_losses[epoch] = train_loss
        test_losses[epoch] = test_loss

        print(f"Epoch {epoch+1}/{epochs}:")
        print(f"Train Loss: {train_loss:.4f}, Train Accuracy: {train_accuracy:.4f}")
        print(f"Test Loss: {test_loss:.4f}, Test Accuracy: {test_accuracy:.4f}")
        print('-'*30)

    return train_losses, test_losses

if __name__ == '__main__':
    # Windows requires this for multiprocessing
    multiprocessing.freeze_support()
    
    # Load the dataset
    dataset = load_data()
    print(f"Dataset loaded successfully with {len(dataset)} images")
    print(f"Classes: {dataset.classes}")
    NUMBER_OF_CLASSES = len(dataset.classes)
    print(f"Number of classes: {NUMBER_OF_CLASSES}")
    
    # Display the first image
    display_image(*dataset[0])
    
    # Split dataset and create dataloaders
    train_dataset, test_dataset = train_test_split(dataset, 0.8)
    batch_size = 32
    
    # Use num_workers=0 on Windows to avoid multiprocessing issues
    train_dataloader = DataLoader(
        train_dataset, batch_size=batch_size, shuffle=True, num_workers=0
    )
    
    test_dataloader = DataLoader(
        test_dataset, batch_size=batch_size, shuffle=False, num_workers=0
    )
    
    # Show a batch of images
    show_batch(train_dataloader)
    
    # Set device (GPU if available, otherwise CPU)
    device = "cpu"
    if torch.cuda.is_available():
        device = "cuda:0"
    elif torch.backends.mps.is_available():
        device = "mps"
    
    print(f"Using device: {device}")
    
    # Initialize the model
    model = CNN(NUMBER_OF_CLASSES).to(device)
    print(model)
    
    # Define loss function and optimizer
    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=0.001)
    
    # Train the model
    train_losses, test_losses = batch_gd(
        model, criterion, optimizer, train_dataloader, test_dataloader, epochs=10
    )
    
    # Plot training and validation loss
    plt.figure(figsize=(10, 6))
    plt.plot(range(1, len(train_losses) + 1), train_losses, label='Training Loss')
    plt.plot(range(1, len(test_losses) + 1), test_losses, label='Validation Loss')
    plt.xlabel('Epochs')
    plt.ylabel('Loss')
    plt.title('Training and Validation Loss')
    plt.legend()
    plt.grid(True)
    plt.savefig('loss_curves.png')
    plt.show()
    
    # Save the model
    torch.save(model.state_dict(), 'eye_disease_classifier.pth')
    print("Model saved successfully!")
    
    # Evaluate on test set with additional metrics
    model.eval()
    conf_matrix = ConfusionMatrix(task="multiclass", num_classes=NUMBER_OF_CLASSES).to(device)
    precision = Precision(task="multiclass", num_classes=NUMBER_OF_CLASSES, average='macro').to(device)
    recall = Recall(task="multiclass", num_classes=NUMBER_OF_CLASSES, average='macro').to(device)
    f1 = F1Score(task="multiclass", num_classes=NUMBER_OF_CLASSES, average='macro').to(device)
    
    all_outputs = []
    all_targets = []
    
    with torch.no_grad():
        for inputs, targets in tqdm(test_dataloader, desc='Evaluating final model'):
            inputs, targets = inputs.to(device), targets.to(device)
            outputs = model(inputs)
            all_outputs.append(outputs)
            all_targets.append(targets)
    
    all_outputs = torch.cat(all_outputs, dim=0)
    all_targets = torch.cat(all_targets, dim=0)
    
    # Calculate metrics
    cm = conf_matrix(all_outputs, all_targets)
    prec = precision(all_outputs, all_targets)
    rec = recall(all_outputs, all_targets)
    f1_score = f1(all_outputs, all_targets)
    
    print(f"Precision: {prec:.4f}")
    print(f"Recall: {rec:.4f}")
    print(f"F1 Score: {f1_score:.4f}")
    
    # Plot confusion matrix
    plt.figure(figsize=(10, 8))
    sns.heatmap(cm.cpu().numpy(), annot=True, fmt='g', cmap='Blues', 
                xticklabels=dataset.classes, yticklabels=dataset.classes)
    plt.xlabel('Predicted labels')
    plt.ylabel('True labels')
    plt.title('Confusion Matrix')
    plt.savefig('confusion_matrix.png')
    plt.show()

    plt.title("Losess")
    plt.plot(train_losses, label="Train loss")
    plt.plot(test_losses, label="Test loss")
    plt.xlabel("Epoch")
    plt.ylabel("Loss")
    plt.legend()
    plt.show()

    y_pred_list = []
    y_true_list = []

    with torch.no_grad():
        for inputs, targets in test_dataloader:
            inputs, targets = inputs.to(device), targets.to(device)
            outputs = model(inputs)
            _,predections = torch.max(outputs, 1)

            y_pred_list.append(targets.cpu().numpy())
            y_true_list.append(predections.cpu().numpy())

    targets = torch.tensor(np.concatenate(y_true_list))
    preds = torch.tensor(np.concatenate(y_pred_list))
    confmat = ConfusionMatrix(task="multiclass", num_classes=NUMBER_OF_CLASSES)
    cm = confmat(preds, targets)
    sns.heatmap(cm, annot=True, fmt=".0f")
    plt.show()
    accuracy = Accuracy(task="multiclass", num_classes=NUMBER_OF_CLASSES).to(device)
    accuracy = accuracy(preds, targets)
    print(f"Accuracy: {100 * accuracy:.2f}%")
    precision = Precision(task="multiclass", average='micro', num_classes=NUMBER_OF_CLASSES)
    precision = precision(preds, targets)
    print(f"Precision: {100 * precision:.2f}%")
    recall = Recall(task="multiclass", average='micro', num_classes=NUMBER_OF_CLASSES)
    recall = recall(preds, targets)
    print(f"Recall: {100 * recall:.2f}%")
    f1 = F1Score(task="multiclass", num_classes=NUMBER_OF_CLASSES)
    f1 = f1(preds, targets)
    print(f"F1 Score: {100 * f1:.2f}%")