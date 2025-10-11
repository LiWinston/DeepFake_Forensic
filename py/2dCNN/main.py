import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from PIL import Image
import cv2
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
import warnings
warnings.filterwarnings('ignore')
import torch
import torch.nn as nn
import torch.optim as optim
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader
import torchvision
import torchvision.transforms as transforms
from torchvision import models
import timm 
np.random.seed(42)
torch.manual_seed(42)
if torch.cuda.is_available():
    torch.cuda.manual_seed(42)
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f"Using device: {device}")
plt.style.use('default')
sns.set_palette("husl")

class ArtDataset(Dataset):
    def __init__(self, data_dir, transform=None, max_samples_per_class=None):
        self.data_dir = data_dir
        self.transform = transform
        self.images = []
        self.labels = []        
        ai_art_dir = os.path.join(data_dir, 'AiArtData', 'AiArtData')
        if os.path.exists(ai_art_dir):
            ai_files = [f for f in os.listdir(ai_art_dir) if f.lower().endswith(('.png', '.jpg', '.jpeg'))]
            if max_samples_per_class:
                ai_files = ai_files[:max_samples_per_class]
            for file in ai_files:
                self.images.append(os.path.join(ai_art_dir, file))
                self.labels.append(0)  # AI Art = 0        
        real_art_dir = os.path.join(data_dir, 'RealArt', 'RealArt')
        if os.path.exists(real_art_dir):
            real_files = [f for f in os.listdir(real_art_dir) if f.lower().endswith(('.png', '.jpg', '.jpeg'))]
            if max_samples_per_class:
                real_files = real_files[:max_samples_per_class]
            for file in real_files:
                self.images.append(os.path.join(real_art_dir, file))
                self.labels.append(1)  # Real Art = 1
        
        print(f"Loaded {len(self.images)} images total")
        print(f"AI Art: {sum(1 for label in self.labels if label == 0)}")
        print(f"Real Art: {sum(1 for label in self.labels if label == 1)}")
    
    def __len__(self):
        return len(self.images)
    
    def __getitem__(self, idx):
        try:
            image_path = self.images[idx]
            image = Image.open(image_path).convert('RGB')
            label = self.labels[idx]
            
            if self.transform:
                image = self.transform(image)
            
            return image, label
        except Exception as e:
            print(f"Error loading image {self.images[idx]}: {e}")
            if self.transform:
                return self.transform(Image.new('RGB', (224, 224), (0, 0, 0))), self.labels[idx]
            return Image.new('RGB', (224, 224), (0, 0, 0)), self.labels[idx]

train_transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.RandomHorizontalFlip(p=0.5),
    transforms.RandomRotation(10),
    transforms.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.2, hue=0.1),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])

val_transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])
data_dir = './data'
full_dataset = ArtDataset(data_dir, transform=None, max_samples_per_class=1000)  # Limit for faster training
train_idx, val_idx = train_test_split(
    range(len(full_dataset)), 
    test_size=0.2, 
    stratify=full_dataset.labels, 
    random_state=42
)
class SubsetDataset(Dataset):
    def __init__(self, dataset, indices, transform=None):
        self.dataset = dataset
        self.indices = indices
        self.transform = transform
    
    def __len__(self):
        return len(self.indices)
    
    def __getitem__(self, idx):
        image_path = self.dataset.images[self.indices[idx]]
        image = Image.open(image_path).convert('RGB')
        label = self.dataset.labels[self.indices[idx]]
        
        if self.transform:
            image = self.transform(image)
        
        return image, label

train_dataset = SubsetDataset(full_dataset, train_idx, train_transform)
val_dataset = SubsetDataset(full_dataset, val_idx, val_transform)
batch_size = 32
train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True, num_workers=4)
val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False, num_workers=4)

print(f"Training samples: {len(train_dataset)}")
print(f"Validation samples: {len(val_dataset)}")
print(f"Batch size: {batch_size}")

class CNNArtDetector(nn.Module):
    def __init__(self, num_classes=2, dropout_rate=0.5):
        super(CNNArtDetector, self).__init__()        
        self.backbone = models.resnet50(pretrained=True)        
        for param in list(self.backbone.parameters())[:-20]:
            param.requires_grad = False        
        num_features = self.backbone.fc.in_features        
        self.backbone.fc = nn.Sequential(
            nn.Dropout(dropout_rate),
            nn.Linear(num_features, 512),
            nn.ReLU(),
            nn.Dropout(dropout_rate),
            nn.Linear(512, 256),
            nn.ReLU(),
            nn.Dropout(dropout_rate),
            nn.Linear(256, num_classes)
        )
    
    def forward(self, x):
        return self.backbone(x)

class CustomCNN(nn.Module):
    def __init__(self, num_classes=2):
        super(CustomCNN, self).__init__()
        self.conv1 = nn.Conv2d(3, 64, kernel_size=3, padding=1)
        self.conv2 = nn.Conv2d(64, 128, kernel_size=3, padding=1)
        self.conv3 = nn.Conv2d(128, 256, kernel_size=3, padding=1)
        self.conv4 = nn.Conv2d(256, 512, kernel_size=3, padding=1)
        
        # Batch normalization
        self.bn1 = nn.BatchNorm2d(64)
        self.bn2 = nn.BatchNorm2d(128)
        self.bn3 = nn.BatchNorm2d(256)
        self.bn4 = nn.BatchNorm2d(512)
        
        # Pooling
        self.pool = nn.MaxPool2d(2, 2)
        self.global_pool = nn.AdaptiveAvgPool2d((1, 1))
        
        # Fully connected layers
        self.fc1 = nn.Linear(512, 256)
        self.fc2 = nn.Linear(256, 128)
        self.fc3 = nn.Linear(128, num_classes)
        
        # Dropout
        self.dropout = nn.Dropout(0.5)
        
    def forward(self, x):
        x = self.pool(F.relu(self.bn1(self.conv1(x))))
        x = self.pool(F.relu(self.bn2(self.conv2(x))))        
        x = self.pool(F.relu(self.bn3(self.conv3(x))))        
        x = self.pool(F.relu(self.bn4(self.conv4(x))))        
        x = self.global_pool(x)
        x = x.view(x.size(0), -1)        
        x = F.relu(self.fc1(x))
        x = self.dropout(x)
        x = F.relu(self.fc2(x))
        x = self.dropout(x)
        x = self.fc3(x)     
        return x

cnn_model = CNNArtDetector(num_classes=2).to(device)
print(f"CNN Model parameters: {sum(p.numel() for p in cnn_model.parameters()):,}")
print(f"Trainable parameters: {sum(p.numel() for p in cnn_model.parameters() if p.requires_grad):,}")

class ViTArtDetector(nn.Module):
    def __init__(self, model_name='vit_base_patch16_224', num_classes=2, pretrained=True):
        super(ViTArtDetector, self).__init__()
        
        self.vit = timm.create_model(model_name, pretrained=pretrained, num_classes=0)  # num_classes=0 removes head        
        self.feature_dim = self.vit.num_features        
        self.classifier = nn.Sequential(
            nn.LayerNorm(self.feature_dim),
            nn.Dropout(0.3),
            nn.Linear(self.feature_dim, 512),
            nn.GELU(),
            nn.Dropout(0.3),
            nn.Linear(512, 256),
            nn.GELU(),
            nn.Dropout(0.3),
            nn.Linear(256, num_classes)
        )
        
        # Freeze some layers (optional)
        # for param in list(self.vit.parameters())[:-10]:
        #     param.requires_grad = False
    
    def forward(self, x):
        features = self.vit(x)        
        output = self.classifier(features)
        
        return output

class CustomViT(nn.Module):
    def __init__(self, img_size=224, patch_size=16, num_classes=2, dim=768, depth=12, heads=12, mlp_dim=3072):
        super(CustomViT, self).__init__()
        
        self.img_size = img_size
        self.patch_size = patch_size
        self.num_patches = (img_size // patch_size) ** 2
        self.patch_dim = 3 * patch_size ** 2        
        self.patch_embedding = nn.Linear(self.patch_dim, dim)        
        self.pos_embedding = nn.Parameter(torch.randn(1, self.num_patches + 1, dim))        
        self.cls_token = nn.Parameter(torch.randn(1, 1, dim))        
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=dim,
            nhead=heads,
            dim_feedforward=mlp_dim,
            dropout=0.1,
            activation='gelu',
            batch_first=True
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=depth)        
        self.classifier = nn.Sequential(
            nn.LayerNorm(dim),
            nn.Linear(dim, num_classes)
        )
    
    def forward(self, x):
        batch_size = x.shape[0]        
        x = x.unfold(2, self.patch_size, self.patch_size).unfold(3, self.patch_size, self.patch_size)
        x = x.contiguous().view(batch_size, -1, self.patch_dim)
        x = self.patch_embedding(x)        
        cls_tokens = self.cls_token.expand(batch_size, -1, -1)
        x = torch.cat([cls_tokens, x], dim=1)
        x += self.pos_embedding    
        x = self.transformer(x)     
        cls_output = x[:, 0]
        output = self.classifier(cls_output)
        
        return output

try:
    vit_model = ViTArtDetector(model_name='vit_base_patch16_224', num_classes=2).to(device)
    print(f"ViT Model parameters: {sum(p.numel() for p in vit_model.parameters()):,}")
    print(f"Trainable parameters: {sum(p.numel() for p in vit_model.parameters() if p.requires_grad):,}")
except Exception as e:
    print(f"Error loading pre-trained ViT: {e}")
    print("Using custom ViT implementation...")
    vit_model = CustomViT(num_classes=2).to(device)
    print(f"Custom ViT Model parameters: {sum(p.numel() for p in vit_model.parameters()):,}")

def train_model(model, train_loader, val_loader, num_epochs=10, learning_rate=1e-4):
    """
    Train the model and return training history
    """
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.AdamW(model.parameters(), lr=learning_rate, weight_decay=1e-4)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=num_epochs)
    
    train_losses = []
    train_accuracies = []
    val_losses = []
    val_accuracies = []
    
    best_val_acc = 0.0
    best_model_state = None
    
    for epoch in range(num_epochs):
        model.train()
        train_loss = 0.0
        train_correct = 0
        train_total = 0
        
        for batch_idx, (data, target) in enumerate(train_loader):
            data, target = data.to(device), target.to(device)
            
            optimizer.zero_grad()
            output = model(data)
            loss = criterion(output, target)
            loss.backward()
            optimizer.step()
            
            train_loss += loss.item()
            _, predicted = torch.max(output.data, 1)
            train_total += target.size(0)
            train_correct += (predicted == target).sum().item()
            
            if batch_idx % 10 == 0:
                print(f'Epoch {epoch+1}/{num_epochs}, Batch {batch_idx}/{len(train_loader)}, Loss: {loss.item():.4f}')
        
        model.eval()
        val_loss = 0.0
        val_correct = 0
        val_total = 0
        
        with torch.no_grad():
            for data, target in val_loader:
                data, target = data.to(device), target.to(device)
                output = model(data)
                loss = criterion(output, target)
                
                val_loss += loss.item()
                _, predicted = torch.max(output.data, 1)
                val_total += target.size(0)
                val_correct += (predicted == target).sum().item()
        
        train_acc = 100 * train_correct / train_total
        val_acc = 100 * val_correct / val_total
        avg_train_loss = train_loss / len(train_loader)
        avg_val_loss = val_loss / len(val_loader)
        
        train_losses.append(avg_train_loss)
        train_accuracies.append(train_acc)
        val_losses.append(avg_val_loss)
        val_accuracies.append(val_acc)
        
        if val_acc > best_val_acc:
            best_val_acc = val_acc
            best_model_state = model.state_dict().copy()
        
        scheduler.step()
        
        print(f'Epoch {epoch+1}/{num_epochs}:')
        print(f'  Train Loss: {avg_train_loss:.4f}, Train Acc: {train_acc:.2f}%')
        print(f'  Val Loss: {avg_val_loss:.4f}, Val Acc: {val_acc:.2f}%')
        print(f'  Learning Rate: {scheduler.get_last_lr()[0]:.6f}')
        print('-' * 50)
    
    model.load_state_dict(best_model_state)
    
    return {
        'train_losses': train_losses,
        'train_accuracies': train_accuracies,
        'val_losses': val_losses,
        'val_accuracies': val_accuracies,
        'best_val_acc': best_val_acc
    }

def evaluate_model(model, test_loader):
    """
    Evaluate the model and return detailed metrics
    """
    model.eval()
    all_predictions = []
    all_targets = []
    
    with torch.no_grad():
        for data, target in test_loader:
            data, target = data.to(device), target.to(device)
            output = model(data)
            _, predicted = torch.max(output, 1)
            
            all_predictions.extend(predicted.cpu().numpy())
            all_targets.extend(target.cpu().numpy())
    
    accuracy = accuracy_score(all_targets, all_predictions)
    report = classification_report(all_targets, all_predictions, 
                                 target_names=['AI Art', 'Real Art'], 
                                 output_dict=True)
    cm = confusion_matrix(all_targets, all_predictions)
    
    return accuracy, report, cm, all_predictions, all_targets

def plot_training_history(history, model_name):
    """
    Plot training and validation metrics
    """
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    fig.suptitle(f'{model_name} Training History', fontsize=16)
    
    axes[0, 0].plot(history['train_losses'], label='Training Loss', color='blue')
    axes[0, 0].plot(history['val_losses'], label='Validation Loss', color='red')
    axes[0, 0].set_title('Loss')
    axes[0, 0].set_xlabel('Epoch')
    axes[0, 0].set_ylabel('Loss')
    axes[0, 0].legend()
    axes[0, 0].grid(True)
    
    axes[0, 1].plot(history['train_accuracies'], label='Training Accuracy', color='blue')
    axes[0, 1].plot(history['val_accuracies'], label='Validation Accuracy', color='red')
    axes[0, 1].set_title('Accuracy')
    axes[0, 1].set_xlabel('Epoch')
    axes[0, 1].set_ylabel('Accuracy (%)')
    axes[0, 1].legend()
    axes[0, 1].grid(True)
    
    axes[1, 0].remove()
    axes[1, 1].remove()
    
    plt.tight_layout()
    plt.show()

def plot_confusion_matrix(cm, model_name):
    """
    Plot confusion matrix
    """
    plt.figure(figsize=(8, 6))
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', 
                xticklabels=['AI Art', 'Real Art'], 
                yticklabels=['AI Art', 'Real Art'])
    plt.title(f'{model_name} - Confusion Matrix')
    plt.xlabel('Predicted')
    plt.ylabel('Actual')
    plt.show()

print("=" * 60)
print("TRAINING CNN MODEL")
print("=" * 60)

num_epochs = 15
learning_rate = 1e-4

cnn_history = train_model(cnn_model, train_loader, val_loader, 
                         num_epochs=num_epochs, learning_rate=learning_rate)

plot_training_history(cnn_history, "CNN (ResNet50)")

print("=" * 60)
print("TRAINING VISION TRANSFORMER MODEL")
print("=" * 60)

num_epochs_vit = 12
learning_rate_vit = 5e-5 

vit_history = train_model(vit_model, train_loader, val_loader, 
                         num_epochs=num_epochs_vit, learning_rate=learning_rate_vit)

plot_training_history(vit_history, "Vision Transformer")

print("=" * 60)
print("EVALUATING CNN MODEL")
print("=" * 60)

cnn_accuracy, cnn_report, cnn_cm, cnn_predictions, cnn_targets = evaluate_model(cnn_model, val_loader)

print(f"CNN Model Accuracy: {cnn_accuracy:.4f}")
print("\nDetailed Classification Report:")
print(classification_report(cnn_targets, cnn_predictions, target_names=['AI Art', 'Real Art']))
plot_confusion_matrix(cnn_cm, "CNN (ResNet50)")
print("=" * 60)
print("EVALUATING VISION TRANSFORMER MODEL")
print("=" * 60)

vit_accuracy, vit_report, vit_cm, vit_predictions, vit_targets = evaluate_model(vit_model, val_loader)

print(f"ViT Model Accuracy: {vit_accuracy:.4f}")
print("\nDetailed Classification Report:")
print(classification_report(vit_targets, vit_predictions, target_names=['AI Art', 'Real Art']))
plot_confusion_matrix(vit_cm, "Vision Transformer")

print("=" * 60)
print("MODEL COMPARISON")
print("=" * 60)

comparison_data = {
    'Model': ['CNN (ResNet50)', 'Vision Transformer'],
    'Accuracy': [cnn_accuracy, vit_accuracy],
    'AI Art Precision': [cnn_report['0']['precision'], vit_report['0']['precision']],
    'AI Art Recall': [cnn_report['0']['recall'], vit_report['0']['recall']],
    'Real Art Precision': [cnn_report['1']['precision'], vit_report['1']['precision']],
    'Real Art Recall': [cnn_report['1']['recall'], vit_report['1']['recall']],
    'F1-Score (Macro)': [cnn_report['macro avg']['f1-score'], vit_report['macro avg']['f1-score']]
}

comparison_df = pd.DataFrame(comparison_data)
print(comparison_df)

fig, axes = plt.subplots(1, 2, figsize=(15, 6))

models = ['CNN (ResNet50)', 'Vision Transformer']
accuracies = [cnn_accuracy, vit_accuracy]
colors = ['skyblue', 'lightcoral']

axes[0].bar(models, accuracies, color=colors)
axes[0].set_title('Model Accuracy Comparison')
axes[0].set_ylabel('Accuracy')
axes[0].set_ylim(0, 1)
for i, v in enumerate(accuracies):
    axes[0].text(i, v + 0.01, f'{v:.3f}', ha='center')

f1_scores = [cnn_report['macro avg']['f1-score'], vit_report['macro avg']['f1-score']]
axes[1].bar(models, f1_scores, color=colors)
axes[1].set_title('F1-Score Comparison')
axes[1].set_ylabel('F1-Score (Macro Average)')
axes[1].set_ylim(0, 1)
for i, v in enumerate(f1_scores):
    axes[1].text(i, v + 0.01, f'{v:.3f}', ha='center')

plt.tight_layout()
plt.show()
torch.save(cnn_model.state_dict(), 'cnn_art_detector.pth')
torch.save(vit_model.state_dict(), 'vit_art_detector.pth')
print("\nModels saved successfully!")
print("\n" + "=" * 60)
print("SUMMARY")
print("=" * 60)
print(f"CNN Model - Best Validation Accuracy: {cnn_history['best_val_acc']:.2f}%")
print(f"ViT Model - Best Validation Accuracy: {vit_history['best_val_acc']:.2f}%")
print(f"\nFinal Test Accuracies:")
print(f"CNN Model: {cnn_accuracy:.4f}")
print(f"ViT Model: {vit_accuracy:.4f}")

if cnn_accuracy > vit_accuracy:
    print(f"\n🏆 CNN Model performs better by {(cnn_accuracy - vit_accuracy):.4f}")
else:
    print(f"\n🏆 ViT Model performs better by {(vit_accuracy - cnn_accuracy):.4f}")

def predict_image(image_path, model, transform, model_name):
    """
    Predict whether an image is AI-generated or real
    """
    try:
        image = Image.open(image_path).convert('RGB')
        image_tensor = transform(image).unsqueeze(0).to(device)        
        model.eval()
        with torch.no_grad():
            output = model(image_tensor)
            probabilities = F.softmax(output, dim=1)
            predicted_class = torch.argmax(output, dim=1).item()
            confidence = probabilities[0][predicted_class].item()
        
        class_names = ['AI Art', 'Real Art']
        prediction = class_names[predicted_class]
        fig, axes = plt.subplots(1, 2, figsize=(12, 5))        
        axes[0].imshow(image)
        axes[0].set_title(f'Input Image')
        axes[0].axis('off')
        
        # Show prediction
        colors = ['red' if i == predicted_class else 'gray' for i in range(2)]
        probs = probabilities[0].cpu().numpy()
        bars = axes[1].bar(class_names, probs, color=colors)
        axes[1].set_title(f'{model_name} Prediction')
        axes[1].set_ylabel('Probability')
        axes[1].set_ylim(0, 1)        
        for bar, prob in zip(bars, probs):
            axes[1].text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.01, 
                        f'{prob:.3f}', ha='center', va='bottom')
        
        plt.tight_layout()
        plt.show()
        
        print(f"{model_name} Prediction: {prediction} (Confidence: {confidence:.3f})")
        print(f"Probabilities - AI Art: {probs[0]:.3f}, Real Art: {probs[1]:.3f}")
        
        return prediction, confidence, probs
        
    except Exception as e:
        print(f"Error processing image: {e}")
        return None, None, None

sample_images = []
ai_art_dir = './data/AiArtData/AiArtData'
real_art_dir = './data/RealArt/RealArt'

if os.path.exists(ai_art_dir):
    ai_files = [f for f in os.listdir(ai_art_dir) if f.lower().endswith(('.png', '.jpg', '.jpeg'))][:2]
    sample_images.extend([os.path.join(ai_art_dir, f) for f in ai_files])

if os.path.exists(real_art_dir):
    real_files = [f for f in os.listdir(real_art_dir) if f.lower().endswith(('.png', '.jpg', '.jpeg'))][:2]
    sample_images.extend([os.path.join(real_art_dir, f) for f in real_files])

for i, image_path in enumerate(sample_images[:4]):
    print(f"\n{'='*60}")
    print(f"TESTING IMAGE {i+1}: {os.path.basename(image_path)}")
    print(f"{'='*60}")
    
    print("CNN Model Prediction:")
    cnn_pred, cnn_conf, cnn_probs = predict_image(image_path, cnn_model, val_transform, "CNN")
    
    print("\nViT Model Prediction:")
    vit_pred, vit_conf, vit_probs = predict_image(image_path, vit_model, val_transform, "ViT")
    
    if cnn_pred == vit_pred:
        print(f"\n✅ Both models agree: {cnn_pred}")
    else:
        print(f"\n❌ Models disagree - CNN: {cnn_pred}, ViT: {vit_pred}")

print("\n🎉 AI Art Detection Analysis Complete!")
