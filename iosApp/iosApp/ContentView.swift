import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Force the view to become first responder after appearing
        DispatchQueue.main.async {
            uiViewController.view.becomeFirstResponder()
        }
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .onAppear {
                // Ensure keyboard can appear
                UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
            }
    }
}
